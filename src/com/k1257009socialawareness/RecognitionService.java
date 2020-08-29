package com.k1257009socialawareness;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;

import weka.classifiers.Classifier;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.Instance;
import weka.core.Instances;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.os.Message;
import android.util.SparseArray;
import android.widget.Toast;
import at.jku.pervasive.sd12.actclient.ClassLabel;
import at.jku.pervasive.sd12.actclient.CoordinatorClient;
import at.jku.pervasive.sd12.actclient.CoordinatorClient.UserState;
import at.jku.pervasive.sd12.actclient.GroupStateListener;
import at.jku.pervasive.sd12.actclient.RoomState;
import at.jku.pervasive.sd12.actclient.UserRole;

/** REMINDERS
 * If you want to reidentify roomState, you need to relaunch this service.
 * Put your training dataset in res/raw and use only lower-case letters in filename. 
 */

/** What's done!
 *  in V0.1:
 *  * Service notification is created
 *  * Training data set is implemented into app. No more need to access phone filesystem.
 *  * communication to server variable is global in whole service now
 *  * correct onStartCommand() implementation
 *  * Minor updates (thread sleep time constant)
 *  in V0.2
 *  * Much more :)..
 */

/**
 * We will use NaiveBayes classifier with X-mean and Z-var features in 1000ms sliding window.
 * Another good alternative is j48 with z-var and z-mean at 1000ms.
 * @author Romantas
 *
 */

public class RecognitionService extends Service implements SensorEventListener {

	static int TRAINING_DATA_SET = R.raw.xmean_zvar_1000;
	static int WINDOW_SIZE = 1000;
	// to store recordings in one window
	static int ARRAY_SIZE = 800;
	static int ARRAY_LENGTH = 2;
	
	static String STUDENT_NR = "1257009";
	static int THREAD_SLEEP_TIME = 500;
	//timeout in ms 
	static int PERSON_ACTIVITY_TIMEOUT = 10000;
	//persons activity collection time window in ms TODO set correct time
	static int COLLECTION_TIME = 60000;
	
	//variables used to collect people in room state
	//determine collection window start
	long ColStartTime;
	//global listener variable
	GroupStateListener gr;
	
	Instances trainingData = null;
	
	// variables used to connect to sensor
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	
	//used in sliding window
	private long startTime = System.currentTimeMillis();
	private float[][] dataArray = new float[ARRAY_SIZE][ARRAY_LENGTH];
	//saves data array index
	private int ai = 0;

	//used in features and classification
	private float xMean, zMean, zVar;
	private Classifier classifier;
	private String[] options;
	Instance vect;
	
	StringBuilder dataString;
	
	//for connection with server
	CoordinatorClient c = null;
	
	//for notification
	private int mId = 19989;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
    	//toast("Created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	createNotificationBar();
    	initiateClassifier();
    	startAccelerometerReader();
    	connectToServer();
    	collectPersonsActivities();
    	return START_STICKY;
    }
    
    private void initiateClassifier(){
    	readTrainingData();
    	createClassifier();
    	trainClassifier();
    }
    
    @Override
	public void onDestroy() {
            super.onDestroy();
            
            // unregister listener
            mSensorManager.unregisterListener(this);
            
            //set default text to UI
            AwareActivity.positionTextField.setText(R.string.default_position);
            AwareActivity.roomState.setText(R.string.default_roomstate);
            AwareActivity.whatIsHappening.setText(R.string.default_whatIsHappening);
            
            //stop the connection to server
        	c.interrupt();
        	
        	stopForeground(true);
            
      		// inform user, that service is stopped.
            toast("Destroyed");
	}
    
    
    private void collectPersonsActivities(){
    	//determine collection window start
    	ColStartTime = System.currentTimeMillis();
    	AwareActivity.whatIsHappening.setText("Collecting info..");
    	
    	gr = new GroupStateListener() {
    		//uses sparse array to save some memory in comparison to ordinary array
    		//because person ID is combined from 7 numbers
    		SparseArray<short[]> db = new SparseArray<short[]>();
        	//temporary variables to store activities
    		short sit = 0, stand = 0, walk = 0, nul = 0;
    		
			@Override
			public void groupStateChanged(UserState[] groupState) {
				
				//check if collection window time isn't over yet
				if(System.currentTimeMillis()-ColStartTime < COLLECTION_TIME){
					for (UserState us : groupState) {
		
						//check if user is offline
						if(us.getUpdateAge() < PERSON_ACTIVITY_TIMEOUT){ 
							//reset temporary variables
							
							//if it isn't a new user
							if (db.get(Integer.parseInt(us.getUserId())) != null){
								short[] person = db.get(Integer.parseInt(us.getUserId()));
								System.out.println("Person "+us.getUserId()+" info:" + Arrays.toString(person) +", update time:"+us.getUpdateAge());
								sit = person[0];
								stand = person[1];
								walk = person[2];
								nul = person[3];
							}
							else{
								sit = 0; stand = 0; walk = 0; nul = 0;
							}
							
							//collect users activities
							if(us.getActivity() == ClassLabel.sitting){
								//System.out.println("SITS "+us.getActivity());
								sit++;
							}
							else if(us.getActivity() == ClassLabel.standing){
								//System.out.println("STANDS "+us.getActivity());
								stand++;
							}
							else if(us.getActivity() == ClassLabel.walking) {
								//System.out.println("WALKS "+us.getActivity());
								walk++;
							}
							else {
								//System.out.println("NULLS "+us.getActivity());
								nul++;
							}
							
							short[] temp = {sit,stand,walk,nul};
							//save collected activities to variable
							try {
								db.put(Integer.parseInt(us.getUserId()),temp);
							} catch(NumberFormatException nfe) {
								System.err.println("ERROR in int converion:"+nfe.toString());
							}
							
						}//end of checking if user is offline
					} //end of iteration trough users
				}//end of checking collection window time
				else{
					printOutDb();
					System.out.println("-----------COLLECTION TIME IS OVER (it took:"+
							(System.currentTimeMillis()-ColStartTime)+"ms)----------");
					
					Message msg = new Message();
			    	String text1 = "";
			    	msg.obj = text1;
			    	AwareActivity.whatIsHappeningHandler.sendMessage(msg);
					
					
					//toast("Collection time is Over \n it took:"+(System.currentTimeMillis()-ColStartTime)+"ms");
					setUserAndRoomStates(groupState);
					c.removeGroupStateListener(gr);
				}
			}// end of groupStateChanged function
			
			private void setUserAndRoomStates(UserState[] gt){
				int id = 0;
				float sum = 0; //used float, to get correct numbers by division
				boolean thereIsSpeaker = false;
				int activePersons = 0, listeners = 0;
				String personRole = "";
				
				Message msg = new Message();
		    	String statusText = "";
		    	
				for (UserState us : gt) {
					//check if user is online
					if(us.getUpdateAge() < PERSON_ACTIVITY_TIMEOUT){ 
						activePersons++;
						id = Integer.parseInt(us.getUserId());
						sum = 0;
				    	short[] a = db.get(id);
				    	for(int numb : a){
				    		sum = sum + numb;
						}
				    	
				    	//determine user state
				    	// 0 - sit, 1 - stand, 2 - walk, 3 -null
				    	//70% is the treshold we think is enough to say, that person is in that state
				    	System.out.println("SUM of activities for "+id+" is:"+ sum+ ", percents:" + a[0]/sum + ","+ a[1]/sum + ","+ a[2]/sum + ","+ a[3]/sum + "." );
				    	if(a[0]/sum > 0.7){
				    		us.setRole(UserRole.listener);
				    		listeners++;
				    		personRole = "listener";
				    		System.out.println("Person "+id+" is listener.");
				    	}
				    	
				    	else if( (a[1]/sum > 0.7) || (a[2]/sum > 0.7) || ( (a[1]/sum > 0.30) && (a[2]/sum > 0.30)) ){
				    		//only one speaker
				    		if(!thereIsSpeaker){
				    			us.setRole(UserRole.speaker);
					    		thereIsSpeaker = true;
					    		personRole = "speaker";
					    		System.out.println("Person "+id+" is speaker.");	
				    		}
				    	}
				    	else{
				    		us.setRole(UserRole.transition);
				    		personRole = "transition";
				    		System.out.println("Person "+id+" is in transition.");
				    	}
				    	
				    	statusText += ""+us.getUserId()+" is "+ us.getActivity() + " (" + personRole + ")\n";

				        personRole = "";
					}//end of check if user is offline
					//if user is offline
					else{
						us.setRole(UserRole.parse("offline"));
					}
				}
				
				//send text to activity
				msg.obj = statusText;
		    	AwareActivity.statusHandler.sendMessage(msg);
				
				System.out.println("speaker:"+thereIsSpeaker+", active persons:"+
						activePersons + ", listeners:" + listeners+", ListenerPerc:" + (float) listeners/activePersons);
				
		    	//set room state
				//there is lecture if listeners are more than 50% of all active persons.
				msg = new Message();
				String roomStateToSend = "";
				
		    	if(thereIsSpeaker && (float) listeners/activePersons > 0.5){
		    		c.setRoomState(RoomState.lecture);
		    		roomStateToSend = "lecture";
		    		System.out.println("There is LECTURE in the room.");
		    	}
		    	else if (activePersons == 0){
		    		c.setRoomState(RoomState.empty);
		    		roomStateToSend = "empty";
		    		System.out.println("The room is EMPTY.");
		    	}
		    	else{
		    		c.setRoomState(RoomState.transition);
		    		roomStateToSend = "transition";
		    		System.out.println("The room is in TRANSITION.");
		    	}
		    	
		    	msg.obj = roomStateToSend;
				AwareActivity.roomStateHandler.sendMessage(msg);
				
			}
			
			private void printOutDb(){
				int key = 0;
				for(int i = 0; i < db.size(); i++) {
				   key = db.keyAt(i);
				   System.out.print("["+ key+"]:");
				   // get the object by the key.
				   short[] a = db.get(key);
				   for(int numb : a){
					   System.out.print(""+numb+",");
				   }
				   System.out.println("");
				}
			}
			
		};//end of groupStateListener
		
		//c.setRoomState(RoomState.transition);
    	c.addGroupStateListener(gr);
    }
    
    private void connectToServer(){
    	c = new CoordinatorClient(STUDENT_NR);
    }
    
    //starts accelerometer listener
    private void startAccelerometerReader(){
    	mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
    	mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    	
    	//register sensor listener
    	mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_FASTEST);
    }
   
    //implements interface
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
	}
		
	// Collects sensor data and sends it to window collector
	public void onSensorChanged(SensorEvent e) {
		collectWindowData(e);
	}
	
	//collects sliding window data ant stores it in variable
    private void collectWindowData(SensorEvent e){
    	//If window time is up create new window
    	if(startTime + WINDOW_SIZE < System.currentTimeMillis()){
    		computeFeatures();
    		startTime = System.currentTimeMillis();
    		//new values for data variable and array indexer
    		dataArray = new float[dataArray.length][ARRAY_LENGTH];
    		ai = 0;
    	}
    	// otherwise add data to variable
    	else{
    		dataArray[ai][0] = e.values[0];
    		dataArray[ai][1] = e.values[2];
    		ai++;
    	}
    }
    
    private void computeFeatures(){
    	//Calculate means
    	for (int n = 0; n < ai; n++) {
    		xMean += dataArray[n][0];
    		zMean += dataArray[n][1];
    	}
    	xMean = xMean/ai;
    	zMean = zMean/ai;
    	
    	//Calculate variance
    	float tempz = 0;
        for (int n = 0; n < ai; n++) {
        	tempz += (dataArray[n][1]-zMean)*(dataArray[n][1]-zMean);
        }  
        zVar = tempz/ai;
    	
    	classifyData();
    }

    //creates features vector and then classifies it
    public void classifyData(){
    	vect= new Instance(2);
    	vect.setValue(0, xMean);
    	vect.setValue(1, zVar);
    	vect.setDataset(trainingData);
    	double clLabel = 0;
		try {
			clLabel = classifier.classifyInstance(vect);
		} catch (Exception e) {
			e.printStackTrace();
			toast("Classification error: "+e.toString());
		}
		String label = vect.classAttribute().value((int)clLabel);
		
		//updates position and debugging info on screen
		AwareActivity.positionTextField.setText(label);
    	//deb("records:"+ai+ " window_size:"+WINDOW_SIZE+ "\n vect:"+vect.toString());
    	
    	sendPositionToServer(label);
    }
    
    //reads training data set from app's resource and stores it in variable
    private void readTrainingData(){
    	
    	//read dataset from app's resource
        InputStream in_s = getResources().openRawResource(TRAINING_DATA_SET);       
        BufferedReader reader = new BufferedReader(new InputStreamReader(in_s));
        try {
			trainingData = new Instances(reader);
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			toast("Reading training data error");
		}
        
        if (trainingData.classIndex() == -1){
    		trainingData.setClassIndex(trainingData.numAttributes() - 1);
    	}
    	
    }
    
    private void createClassifier(){ 	
//    	options = new String[] { "-C 0.8 -M 2" };
//    	classifier = new J48();
    	
    	options = new String[] { "" };
    	classifier = new NaiveBayes();
    	
    	trainClassifier();
    	
    }
    
    //trains classifier with training data set
    private void trainClassifier(){
    	//toast("Training classifier");
    	try {
			classifier.setOptions(options);
			classifier.buildClassifier(trainingData);
		} catch (Exception e) {
			e.printStackTrace();
			toast("trainClassifier ERROR");
		}
    
    }
    
    //sends data to server
    public void sendPositionToServer(String label){
    	//check connection to server
    	if (!c.isAlive()){
    		toast("Connection to server is down");
    	}

    	// send activity update
    	try {
    		c.setCurrentActivity(ClassLabel.valueOf(label));
    		Thread.sleep(THREAD_SLEEP_TIME);
    		//System.out.println("position:" +label + " records:"+ ai + " vec:"+vect.toString());
    	} catch (Exception e) {
    		e.printStackTrace();
    		toast("Sending eror: "+e.toString());
    	}
    }
    
    private void createNotificationBar(){
    	Notification notification = new Notification(R.drawable.ic_launcher,
		                "Social awareness service started",
		                System.currentTimeMillis());
		Intent i=new Intent(this, AwareActivity.class);
		
		//don't start any new activities if it is running already
		i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
		
		PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);
		
		notification.setLatestEventInfo(this, "Social Awareness", "Service is running", pi);
		//don't clear notification when user clicks "clear all" in notification bar
		notification.flags|=Notification.FLAG_NO_CLEAR;
		
		startForeground(mId, notification);
    }

    private void toast(String str){
      	Toast.makeText(this, "Service: "+str, Toast.LENGTH_SHORT).show();
    }
    
}