package com.k1257009socialawareness;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;



public class AwareActivity extends Activity {

	protected ToggleButton button;
	public static TextView positionTextField, visualSpace, roomState,whatIsHappening;
	RecognitionService recService;
	
	
	//creates view and assigns variables to view objects
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_aware);
        
        
        button = (ToggleButton) findViewById(R.id.btnToggleService);
        positionTextField = (TextView) findViewById(R.id.positionTextView);
        visualSpace = (TextView) findViewById(R.id.statusText);
        roomState = (TextView) findViewById(R.id.roomStateView01);
        whatIsHappening = (TextView) findViewById(R.id.whatIsHappening);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_aware, menu);
        return true;
    }
    
 	// Called when the user clicks the toggle button
    public void toggleService(View view) {
    	
    	Intent intent = new Intent(this, RecognitionService.class);
    	
    	if( button.isChecked() ){
    		//toast("service on");
    		startService(intent);
    	}
    	else{
    		//toast("service off");
    		stopService(intent);
    	}
    }
    
    public void changeText(String t){
    	roomState.setText(t);
    }
    
    static Handler roomStateHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String text = (String)msg.obj;
            roomState.setText(text);
        }
    };
    
    static Handler whatIsHappeningHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String text = (String)msg.obj;
            whatIsHappening.setText(text);
        }
    };
    
    static Handler statusHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            String text = (String)msg.obj;
            visualSpace.setText(text);
        }
    };
    
    private void toast(String str){
    	Toast.makeText(this, "Activity: "+str, Toast.LENGTH_SHORT).show();
    }

}
