package com.rethoughtsolutions.swipecardentry.sample;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.rethoughtsolutions.swipecardentry.SwipeCardEntry;

public class MainActivity extends Activity {

    private SwipeCardEntry mSwipeCardEntry;

    private Button mPayButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSwipeCardEntry = (SwipeCardEntry) findViewById(R.id.swipecardentry);
        mPayButton = (Button) findViewById(R.id.paybutton);


        mSwipeCardEntry.setListener(new SwipeCardEntry.Listener() {
            @Override
            public void onCardEntryCompleted(boolean completed) {
                mPayButton.setEnabled(completed);
            }
        });

        mPayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this,"Pay button clicked!",Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mPayButton.setEnabled(mSwipeCardEntry.isCompleted());
    }
}
