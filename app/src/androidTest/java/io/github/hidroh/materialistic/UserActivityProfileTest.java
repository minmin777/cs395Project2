package io.github.hidroh.materialistic;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.content.Context;
import android.media.Image;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.test.espresso.ViewInteraction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ServiceTestCase;
import android.test.suitebuilder.annotation.LargeTest;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.lang.reflect.Method;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class UserActivityProfileTest {



    @Rule
    public ActivityTestRule<UserActivity> mActivityRule = new ActivityTestRule<>(UserActivity.class);
    @Before
    public void setUp() throws Exception {



    }

    @After
    public void tearDown() throws Exception {
    }
// this tests that the picture id in firebase and the picture id that the picture shown in the app have the same id
    /* had a problem with this because i tried setting the imagebutton tag to the id but when I wanted to put the new profile picture
    using Glide the app would crash and would say "You must not call setTag() on a view Glide is targeting" so I cannot use this test 
     */
/*    @Test
    public void profile(){

        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference("Users");
        String mUsername = Preferences.getUsername(mActivityRule.getActivity().getApplicationContext());
        Log.d("testprofile", mUsername);

        rootRef.child("name").child(mUsername).child("profilepicture").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if(dataSnapshot.getValue() != null){
                    ImageButton ib = mActivityRule.getActivity().findViewById(R.id.image_button_android);
                    String value = dataSnapshot.getValue().toString();
                    String ibb = ib.getTag().toString();
                    assertEquals(value, ibb);
                    //onView(withId(R.id.image_button_android));
                    //onView(allOf(withId(R.id.image_button_android), withTagValue(is((Object) "some_text")))).check(matches(isDisplayed()));

                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


    }*/

    // this tests that you can click on the imagebutton and allows you to pick a picture
    @Test
    public void addProfilePcitureTest() {    ViewInteraction appCompatImageButton2 = onView(
            allOf(withId(R.id.image_button_android),
                    childAtPosition(
                            allOf(withId(R.id.container),
                                    childAtPosition(
                                            withId(R.id.header_card_view),
                                            0)),
                            0),
                    isDisplayed()));
        appCompatImageButton2.perform(click());

    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}