package com.rethoughtsolutions.swipecardentry.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;


public class MainActivity extends Activity {

    private TestSwipeCardEntry mSwipeCardEntry;

    private Button mPayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSwipeCardEntry = (TestSwipeCardEntry) findViewById(R.id.swipecardentry);
        mPayButton = (Button) findViewById(R.id.paybutton);


        mSwipeCardEntry.setListener(new TestSwipeCardEntry.Listener() {
            @Override
            public void onCardEntryCompleted(boolean completed) {
                mPayButton.setEnabled(completed);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPayButton.setEnabled(mSwipeCardEntry.isCompleted());
    }
}
