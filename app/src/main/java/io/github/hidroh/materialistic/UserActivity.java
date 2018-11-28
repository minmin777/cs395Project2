/*
 * Copyright (c) 2015 Ha Duy Trung
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.hidroh.materialistic;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v7.widget.RecyclerView;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import io.github.hidroh.materialistic.annotation.Synthetic;
import io.github.hidroh.materialistic.data.ItemManager;
import io.github.hidroh.materialistic.data.ResponseListener;
import io.github.hidroh.materialistic.data.UserItem;
import io.github.hidroh.materialistic.data.UserManager;
import io.github.hidroh.materialistic.widget.CommentItemDecoration;
import io.github.hidroh.materialistic.widget.SnappyLinearLayoutManager;
import io.github.hidroh.materialistic.widget.SubmissionRecyclerViewAdapter;


import com.bumptech.glide.Glide;
import com.bumptech.glide.Registry;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;
import com.firebase.ui.storage.images.FirebaseImageLoader;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

public class UserActivity extends InjectableActivity implements Scrollable {
    public static final String EXTRA_USERNAME = UserActivity.class.getName() + ".EXTRA_USERNAME";
    private static final String STATE_USER = "state:user";
    private static final String PARAM_ID = "id";
    private static final String KARMA = " (%1$s)";
    @Inject UserManager mUserManager;
    @Inject @Named(ActivityModule.HN) ItemManager mItemManger;
    @Inject KeyDelegate mKeyDelegate;
    private KeyDelegate.RecyclerViewHelper mScrollableHelper;
    private String mUsername;
    private UserItem mUserItem;
    private UserManager.User mUser;
    private TextView mTitle;
    private TextView mInfo;
    private TextView mAbout;
    @Synthetic RecyclerView mRecyclerView;
    private TabLayout mTabLayout;
    private View mEmpty;
    private BottomSheetBehavior<View> mBottomSheetBehavior;
    private ImageButton androidImageButton;
    private static int RESULT_LOAD_IMAGE = 1;
    //FirebaseStorage storage = FirebaseStorage.getInstance();
    //StorageReference storageRef = storage.getReferenceFromUrl("https://materialistic-profile.firebaseio.com/");

    DatabaseReference rootRef;

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        rootRef = FirebaseDatabase.getInstance().getReference("Users");
        mUsername = getIntent().getStringExtra(EXTRA_USERNAME);
        if (TextUtils.isEmpty(mUsername)) {
            mUsername = AppUtils.getDataUriId(getIntent(), PARAM_ID);
        }
        if (TextUtils.isEmpty(mUsername)) {
            finish();
            return;
        }

        setTaskTitle(mUsername);
        AppUtils.setStatusBarDim(getWindow(), true);
        setContentView(R.layout.activity_user);
        findViewById(R.id.touch_outside).setOnClickListener(v -> finish());
        mBottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.bottom_sheet));
        mBottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_HIDDEN:
                        finish();
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        AppUtils.setStatusBarDim(getWindow(), false);
                        mRecyclerView.setLayoutFrozen(false);
                        break;
                    default:
                        AppUtils.setStatusBarDim(getWindow(), true);
                        break;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                // no op
            }
        });
        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setText(mUsername);
        mInfo = (TextView) findViewById(R.id.user_info);
        mAbout = (TextView) findViewById(R.id.about);
        mEmpty = findViewById(R.id.empty);
        mTabLayout = (TabLayout) findViewById(R.id.tab_layout);
        mTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // no op
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // no op
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                scrollToTop();
            }
        });
        addUser();
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        mRecyclerView.setLayoutManager(new SnappyLinearLayoutManager(this, true));
        mRecyclerView.addItemDecoration(new CommentItemDecoration(this));
        mScrollableHelper = new KeyDelegate.RecyclerViewHelper(mRecyclerView,
                KeyDelegate.RecyclerViewHelper.SCROLL_ITEM);
        if (savedInstanceState != null) {
            mUser = savedInstanceState.getParcelable(STATE_USER);
            mUserItem = new UserItem(savedInstanceState.getParcelable(STATE_USER));
        }
        if (mUser == null) {
            load();
        } else {
            bind();
        }


        if (!AppUtils.hasConnection(this)) {
            Snackbar.make(findViewById(R.id.content_frame),
                    R.string.offline_notice, Snackbar.LENGTH_LONG)
                    .show();
        }
        if (isStoragePermissionGranted()) {
            androidImageButton = (ImageButton) findViewById(R.id.image_button_android);
        }
        putProfilePictureInButton();



            androidImageButton.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {

                    Intent i = new Intent(
                            Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    i.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivityForResult(i, RESULT_LOAD_IMAGE);
                    Toast.makeText(UserActivity.this, "It works", Toast.LENGTH_LONG).show();


                }
            });

    }
    public void putProfilePictureInButton(){

        rootRef.child("name").child(mUsername).child("profilepicture").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                if(dataSnapshot.getValue() != null){
                    Log.d("getprofile", "in if");
                    String st = dataSnapshot.getValue().toString();
                    StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl("gs://materialistic-profile.appspot.com").child("images/" + st);
                    storageReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                        @Override
                        public void onSuccess(Uri uri) {
                            String uu = uri.toString();
                            Glide.with(androidImageButton.getContext()).load(uu).apply(new RequestOptions().override(200, 200)).into(androidImageButton);

                        }
                    });
                }



            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("putprofilepicture", databaseError.toString());
            }
        });
    }
    public void addUser(){
        androidImageButton = (ImageButton) findViewById(R.id.image_button_android);
        rootRef.child("name").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(!dataSnapshot.hasChild(mUsername)){
                    rootRef.child("name").child(mUsername);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

    }
    public  boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("permission","Permission is granted");
                return true;
            } else {

                Log.v("permission","Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v("permission","Permission is granted");
            return true;
        }
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(grantResults[0]== PackageManager.PERMISSION_GRANTED){
            Log.v("permission","Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RESULT_LOAD_IMAGE && resultCode == RESULT_OK) {
            Uri selectedImage = data.getData();
            Log.d("selectedImage", selectedImage.toString());
            String[] filePathColumn = { MediaStore.Images.Media.DATA };

            Cursor cursor = getContentResolver().query(selectedImage,
                    filePathColumn, null, null, null);
            cursor.moveToFirst();

            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            String picturePath = cursor.getString(columnIndex);

            cursor.close();

                androidImageButton = (ImageButton) findViewById(R.id.image_button_android);
                androidImageButton.setImageBitmap(BitmapFactory.decodeFile(picturePath));



            FirebaseStorage storage = FirebaseStorage.getInstance();
            StorageReference storageRef = storage.getReferenceFromUrl("gs://materialistic-profile.appspot.com");
            String uripath = UUID.randomUUID().toString();

            StorageReference childRef = storageRef.child("images/" + uripath);


            Uri ii = Uri.fromFile(new File(picturePath));
            UploadTask uploadTask = childRef.putFile(ii);
            // below deletes the existing image from storage as we are changing the picture
            rootRef.child("name").child(mUsername).child("profilepicture").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if(dataSnapshot.getValue() != null){
                        String referenceid = dataSnapshot.getValue().toString();
                        StorageReference storageReference = FirebaseStorage.getInstance().getReferenceFromUrl("gs://materialistic-profile.appspot.com").child("images/" + referenceid);
                        storageReference.delete().addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void aVoid) {
                                // File deleted successfully
                                Toast.makeText(UserActivity.this, "Delete successful", Toast.LENGTH_LONG).show();
                                Log.d("deleteprofilepicture", "onSuccess: deleted file");
                            }
                        }).addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(@NonNull Exception exception) {
                                // Uh-oh, an error occurred!
                                Log.d("deleteprofilepicture", "onFailure: did not delete file");
                            }
                        });
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) {

                }
            });
            addProfilePicture(uripath);

            uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
                @Override
                public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                    Toast.makeText(UserActivity.this, "Upload successful", Toast.LENGTH_LONG).show();
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {

                    Toast.makeText(UserActivity.this, "Upload Failed -> " + e, Toast.LENGTH_LONG).show();
                    Log.d("uploadTask", "Upload Failed ->" + e);
                }
            });

        }


    }

    public void addProfilePicture(String ii){
        rootRef.child("name").child(mUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {



                rootRef.child("name").child(mUsername).child("profilepicture").setValue(ii);

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.d("addprofilepicture", databaseError.toString());
            }
        });
    }
    @Override
    protected void onStart() {
        super.onStart();
        mKeyDelegate.attach(this);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(STATE_USER, mUser);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mKeyDelegate.detach(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mKeyDelegate.setScrollable(this, null);
        return mKeyDelegate.onKeyDown(keyCode, event) ||
                super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return mKeyDelegate.onKeyUp(keyCode, event) ||
                super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return mKeyDelegate.onKeyLongPress(keyCode, event) ||
                super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void scrollToTop() {
        mScrollableHelper.scrollToTop();
    }

    @Override
    public boolean scrollToNext() {
        return mScrollableHelper.scrollToNext();
    }

    @Override
    public boolean scrollToPrevious() {
        return mScrollableHelper.scrollToPrevious();
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    private void load() {
        mUserManager.getUser(mUsername, new UserResponseListener(this));
    }

    @Synthetic
    void onUserLoaded(UserManager.User response) {
        if (response != null) {
            mUser = response;
            bind();
        } else {
            showEmpty();
        }
    }

    private void showEmpty() {
        mInfo.setVisibility(View.GONE);
        mAbout.setVisibility(View.GONE);
        mEmpty.setVisibility(View.VISIBLE);
        mTabLayout.addTab(mTabLayout.newTab()
                .setText(getResources().getQuantityString(R.plurals.submissions_count, 0, "").trim()));
    }

    private void bind() {
        SpannableString karma = new SpannableString(String.format(Locale.US, KARMA,
                NumberFormat.getInstance(Locale.getDefault()).format(mUser.getKarma())));
        karma.setSpan(new RelativeSizeSpan(0.8f), 0, karma.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
        mTitle.append(karma);
        mInfo.setText(getString(R.string.user_info, mUser.getCreated(this)));
        if (TextUtils.isEmpty(mUser.getAbout())) {
            mAbout.setVisibility(View.GONE);
        } else {
            AppUtils.setTextWithLinks(mAbout, AppUtils.fromHtml(mUser.getAbout(), true));
        }
        int count = mUser.getItems().length;
        mTabLayout.addTab(mTabLayout.newTab()
                .setText(getResources().getQuantityString(R.plurals.submissions_count, count, count)));
        mRecyclerView.setAdapter(new SubmissionRecyclerViewAdapter(mItemManger, mUser.getItems()));
        mRecyclerView.setLayoutFrozen(mBottomSheetBehavior.getState() !=
                BottomSheetBehavior.STATE_EXPANDED);
    }

    static class UserResponseListener implements ResponseListener<UserManager.User> {
        private final WeakReference<UserActivity> mUserActivity;

        @Synthetic
        UserResponseListener(UserActivity userActivity) {
            mUserActivity = new WeakReference<>(userActivity);
        }

        @Override
        public void onResponse(@Nullable UserManager.User response) {
            if (mUserActivity.get() != null && !mUserActivity.get().isActivityDestroyed()) {
                mUserActivity.get().onUserLoaded(response);
            }
        }

        @Override
        public void onError(String errorMessage) {
            if (mUserActivity.get() != null && !mUserActivity.get().isActivityDestroyed()) {
                Toast.makeText(mUserActivity.get(), R.string.user_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }
}
