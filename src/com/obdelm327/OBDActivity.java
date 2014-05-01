package com.obdelm327;
import java.util.ArrayList;
import com.obdelm327.R;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class OBDActivity extends Activity {
	private boolean gps=false,gps_enabled=false;
	boolean getname=false,getprotocol=false,commandmode=false,getecho=false,initialized=false,setprotocol=false,fuelc=true;
	String devicename,deviceprotocol,tmpmsg;	
	private int rpmval=0,currenttemp=0,intakeairtemp=0,contmodulevolt=0,ambientairtemp=0,engineoiltemp=0,b1s1temp=0,
			Enginedisplacement=1500,Enginetype=0,FaceColor=0;	
	private ArrayList<Double> avgconsumption;
	GaugeSpeed speed;
	float gpsspeed=0;
	GaugeRpm rpm;
	private static LocationManager locationManager=null;
	private LocationListener locationListener=null;	
    // Debugging
    private static final String TAG = "BluetoothChat";
    private static final boolean D = true;
	private static boolean actionbar=true;
	private static final String[] PIDS = {
         "01","02","03","04","05","06","07","08",
         "09","0A","0B","0C","0D","0E","0F","10",
         "11","12","13","14","15","16","17","18",
         "19","1A","1B","1C","1D","1E","1F","20"};
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;

    // Key names received from the BluetoothChatService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton,mPidsButton,mTroublecodes;
    String currentMessage;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the chat services
    private BluetoothService mChatService = null;
    private TextView Load,Fuel,Volt,Temp,Status,Loadtext,Volttext,Temptext,Centertext,Info;
	String [] commands;
	String [] initializeCommands;
	int whichCommand=0;
	String ENGINE_COOLANT_TEMP = "0105",  //A-40
		   ENGINE_RPM          = "010C",  //((A*256)+B)/4
		   ENGINE_LOAD         = "0104",  // A*100/255
		   VEHICLE_SPEED       = "010D",  //A
		   INTAKE_AIR_TEMP     = "010F",  //A-40
		   CONT_MODULE_VOLT    = "0142",  //((A*256)+B)/1000
		   AMBIENT_AIR_TEMP    = "0146",  //A-40
		   ENGINE_OIL_TEMP     = "015C",  //A-40		   
		   CATALYST_TEMP_B1S1  = "013C";  //(((A*256)+B)/10)-40
	String supportedPIDs       = "0100";
	String troubleCodes        = "03"; 
	private PowerManager.WakeLock wl;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wl = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
		wl.acquire();
        setContentView(R.layout.activity_main);   
        PackageManager PM = getPackageManager();       
		gps = PM.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);				
        Status=(TextView) findViewById(R.id.Status);				   
	    Load = (TextView) findViewById(R.id.Load);	   
	    Fuel= (TextView) findViewById(R.id.Fuel);	  
	    Temp= (TextView) findViewById(R.id.Temp);	   
	    Volt= (TextView) findViewById(R.id.Volt);	 
	    Loadtext = (TextView) findViewById(R.id.Load_text);	  
	    Temptext= (TextView) findViewById(R.id.Temp_text);	   
	    Volttext= (TextView) findViewById(R.id.Volt_text);	
	    Centertext=(TextView) findViewById(R.id.Center_text);	
	    Info=(TextView) findViewById(R.id.info);	
	    speed=(GaugeSpeed) findViewById(R.id.GaugeSpeed);	
	    rpm=(GaugeRpm) findViewById(R.id.GaugeRpm);
	    avgconsumption = new ArrayList<Double>();	
	   
	    commands = new String[]{"ATRV",ENGINE_RPM,ENGINE_LOAD,VEHICLE_SPEED,ENGINE_COOLANT_TEMP};
        initializeCommands = new String[]{"ATDP","ATS0","ATL0","ATAT0","ATST10","ATSPA0","ATE0"};
		whichCommand=0;		
		RelativeLayout rlayout = (RelativeLayout) findViewById(R.id.mainscreen);
		  rlayout.setOnClickListener(new OnClickListener() {

		        @Override
		        public void onClick(View v) {
		        	if(actionbar)
		        	{
			        	getActionBar().hide();
			        	actionbar=false;
		        	}
		        	else{
		        		getActionBar().show();
			        	actionbar=true;
		        	}	
		        }
		   });
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();        
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }     
        if (mChatService != null) {            
            if (mChatService.getState() == BluetoothService.STATE_NONE) {             
              mChatService.start();
            }
        }
        
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);        
        } else {
            if (mChatService == null) setupChat();
        }
        speed.setTargetValue(200);
	    rpm.setTargetValue(8000);
	    if(gps)
	    {
		    locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE); 
		    try{
				gps_enabled=locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
				}catch(Exception ex){}   
		    if(gps_enabled)
			{				
				initGps();		 			
			}
	    }
	    new Thread(new Runnable() {
	        @Override
	        public void run() {
	            try {
	                Thread.sleep(1500);
	            } catch (InterruptedException e) {
	                e.printStackTrace();
	            }
	            runOnUiThread(new Runnable() {
	                @Override
	                public void run() {
	                	speed.setTargetValue(0);
	            	    rpm.setTargetValue(0);
	                }
	            });
	        }
	    }).start();      
	    
    }  
    
    private void setDefaultOrientation(){		
        try {	
        settextsixe();
		setgaugesize();	
        } catch (Exception e) {}
    }
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		 super.onConfigurationChanged(newConfig);
		 setDefaultOrientation();
	}
    @Override
    public synchronized void onResume() {
        super.onResume(); 
        
        setDefaultOrientation();	
        hideVirturalKeyboard();   
        SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(getBaseContext());	
        FaceColor=Integer.parseInt(preferences.getString("FaceColor", "0"));         
        rpm.setFace(FaceColor);        
	    speed.setFace(FaceColor);	
 	    Enginedisplacement=Integer.parseInt(preferences.getString("Enginedisplacement", "1500"));  	   
 	    Enginetype=Integer.parseInt(preferences.getString("EngineType", "0"));  
	    Enginedisplacement=Enginedisplacement/1500;   
	    
    }  
    GpsStatus.Listener gpsStatusListener=null;
	@SuppressLint("NewApi")
	protected void initGps() {	  
     	  Criteria myCriteria = new Criteria();
     	  myCriteria.setAccuracy(Criteria.ACCURACY_FINE);      
     	  locationListener = new LocationListener() 
           {     
     		public void onLocationChanged(Location location) {   			
           	try
  				{            		             		
     			gpsspeed = location.getSpeed() * 3600 / 1000;  
     			speed.setTargetValue(gpsspeed);     					
       			}catch(Exception e){}              
              }
			@Override
			public void onProviderDisabled(String provider) {
				// TODO Auto-generated method stub				
			}
			@Override
			public void onProviderEnabled(String provider) {
				// TODO Auto-generated method stub				
			}
			@Override
			public void onStatusChanged(String provider, int status,Bundle extras) {
				// TODO Auto-generated method stub				
			}     		
          };    
         locationManager.requestLocationUpdates(0L, // minTime
     				0.0f, // minDistance
     				myCriteria, // criteria
     				locationListener, // listener
     				null); // looper	
         gpsStatusListener = new GpsStatus.Listener() {
              @Override
              public void onGpsStatusChanged(int event) {                
                  if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS || event == GpsStatus.GPS_EVENT_FIRST_FIX) {
                      GpsStatus status = locationManager.getGpsStatus(null);
                      if(status != null) {
                      	Iterable<GpsSatellite> sats = status.getSatellites();
                      	int i=0;                    
                          for (GpsSatellite sat : sats) { 
                        	  i++;                        	 
                        	  if(!tryconnect)
                        	  {
                        		  if(sat.usedInFix())                        		 
                        			  Status.setText("Gps Active");  
                        		  else
                        			  Status.setText("Checking Gps - Sats: " + String.valueOf(i));	   
                        	  }
                          }                          
                      }                   	
                  }
              }
          };
          locationManager.addGpsStatusListener(gpsStatusListener);
      }
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.message);
        mConversationView = (ListView) findViewById(R.id.in);
        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);
        
        mPidsButton = (Button) findViewById(R.id.button_pids);
        mPidsButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {                
                sendMessage(supportedPIDs);                
            }
        });		
        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString();
                sendMessage(message);
                //view.setText(message);
            }
        });		
        mTroublecodes= (Button) findViewById(R.id.button_troublecodes);
        mTroublecodes.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {                
                sendMessage(troubleCodes);  
             // ------------------------------------------------------------------------
                // OBDII code examples
                //  Example: P0171
                // 1st character identifies the system related to the trouble code.
                //  P = Powertrain
                //  B = Body
                //  C = Chassis
                //  U = Undefined
                // 2nd digit identifies whether the code is a generic code (same on all 
                // OBD-II equpped vehicles), or a manufacturer specific code.
                //  0 = Generic (this is the digit zero -- not the letter "O")
                //  1 = Enhanced (manufacturer specific)
                // 3rd digit denotes the type of subsystem that pertains to the code
                //  0 = Fuel and Air Metering and Auxilliary Emission Controls
                //  1 = Emission Management (Fuel or Air)
                //  2 = Injector Circuit (Fuel or Air)
                //  3 = Ignition or Misfire
                //  4 = Auxilliary Emission Control
                //  5 = Vehicle Speed & Idle Control
                //  6 = Computer & Output Circuit
                //  7 = Transmission
                //  8 = Transmission
                //  9 = SAE Reserved
                //  0 = SAE Reserved
                // 4th/5th digits, along with the others, are variable, and relate to a 
                // particular problem. 
            }
        });		
        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothService(this, mHandler);
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");     
        invisiblecmd();
    }
    public void resetvalues()
   	{    	 
		 Load.setText("0 %");
	     Fuel.setText("0 / 0 L/h");
	     Volt.setText("0 V");
	     Temp.setText("0 C°");
	     avgconsumption.clear();
	     initialized=false;
	     speed.setTargetValue(200);
		 rpm.setTargetValue(8000);	
		    new Thread(new Runnable() {
		        @Override
		        public void run() {
		            try {
		                Thread.sleep(1500);
		            } catch (InterruptedException e) {
		                e.printStackTrace();
		            }
		            runOnUiThread(new Runnable() {
		                @Override
		                public void run() {
		                	speed.setTargetValue(0);
		            	    rpm.setTargetValue(0);
		                }
		            });
		        }
		    }).start();		   
   	}
    public void invisiblecmd()
	{
		 mConversationView.setVisibility(View.INVISIBLE);
	     mOutEditText.setVisibility(View.INVISIBLE);
	     mSendButton.setVisibility(View.INVISIBLE);
	     mPidsButton.setVisibility(View.INVISIBLE);
	     mTroublecodes.setVisibility(View.INVISIBLE);
	     rpm.setVisibility(View.VISIBLE);
		 speed.setVisibility(View.VISIBLE);
		 Load.setVisibility(View.VISIBLE);
	     Fuel.setVisibility(View.VISIBLE);
	     Volt.setVisibility(View.VISIBLE);
	     Temp.setVisibility(View.VISIBLE);			       
	     Loadtext.setVisibility(View.VISIBLE);			      
	     Volttext.setVisibility(View.VISIBLE);
	     Temptext.setVisibility(View.VISIBLE);	
	     Centertext.setVisibility(View.VISIBLE); 
	}
	public void visiblecmd()
	{
		 rpm.setVisibility(View.INVISIBLE);
		 speed.setVisibility(View.INVISIBLE);
		 Load.setVisibility(View.INVISIBLE);
         Fuel.setVisibility(View.INVISIBLE);
         Volt.setVisibility(View.INVISIBLE);
         Temp.setVisibility(View.INVISIBLE);      
         Loadtext.setVisibility(View.INVISIBLE);       
         Volttext.setVisibility(View.INVISIBLE);
         Temptext.setVisibility(View.INVISIBLE);         
         Centertext.setVisibility(View.INVISIBLE);  
		 mConversationView.setVisibility(View.VISIBLE);
	     mOutEditText.setVisibility(View.VISIBLE);
	     mSendButton.setVisibility(View.VISIBLE);
	     mPidsButton.setVisibility(View.VISIBLE);
	     mTroublecodes.setVisibility(View.VISIBLE);
	}
	private void setgaugesize()
	{ 
		 Display display = getWindow().getWindowManager().getDefaultDisplay();	   
		    int width = 0;
		    int height = 0;
		    width = display.getWidth();
	        height = display.getHeight();      
	       if(width>height)
		    { 
		    	RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(3*height/4, 3*height/4);	
		    	lp.addRule(RelativeLayout.BELOW,findViewById(R.id.Load).getId());
		    	lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);		
		    	lp.setMargins(5,5,5,5);
		        rpm.setLayoutParams(lp);  
		        lp = new RelativeLayout.LayoutParams(3*height/4, 3*height/4);
		        lp.addRule(RelativeLayout.BELOW,findViewById(R.id.Volt).getId());
		    	lp.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		    	lp.setMargins(5,5,5,5);
		        speed.setLayoutParams(lp);
		        
		    }else if(width<height){		    	
		    	RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(3*width/4, 3*width/4);  
		    	lp.addRule(RelativeLayout.BELOW,findViewById(R.id.Fuel).getId());
		    	lp.addRule(RelativeLayout.CENTER_HORIZONTAL);  
		    	lp.setMargins(5,5,5,5);
	    		rpm.setLayoutParams(lp);  	    		
	    	    lp = new RelativeLayout.LayoutParams(3*width/4, 3*width/4);	    	    
	    	    lp.addRule(RelativeLayout.BELOW,findViewById(R.id.GaugeRpm).getId());
	    	    lp.addRule(RelativeLayout.CENTER_HORIZONTAL);	
	    	    lp.setMargins(5,5,5,5);
		    	speed.setLayoutParams(lp);	
		    	
		    }	 
	}
	
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "- ON PAUSE -");
    }

    @Override
    public void onStop() {
        super.onStop();       
    }

    @SuppressLint("Wakelock")
	@Override
    public void onDestroy() {
        super.onDestroy();        
        if (mChatService != null) mChatService.stop();
        try
			{
    		if (locationManager != null)
    		{
    		locationManager.removeGpsStatusListener(gpsStatusListener);
    		locationManager.removeUpdates(locationListener);                		
    		}			
			}catch(Exception e){}      
        wl.release();	
    }

    private void ensureDiscoverable() {
        if(D) Log.d(TAG, "ensure discoverable");
        if (mBluetoothAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(this, R.string.not_connected, Toast.LENGTH_LONG).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {     
        	message=message+"\r";
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };
 boolean tryconnect=false;
    // The Handler that gets information back from the BluetoothChatService
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                switch (msg.arg1) {
                case BluetoothService.STATE_CONNECTED:  
                	try
      				{
                		if (locationManager != null)
                		{
                		locationManager.removeGpsStatusListener(gpsStatusListener);
                		locationManager.removeUpdates(locationListener);   
                		speed.setTargetValue(0);
	            	    rpm.setTargetValue(0);	            	    
                		}			
           			}catch(Exception e){}                     	
                    Status.setText(getString(R.string.title_connected_to, mConnectedDeviceName));
                	OBDActivity.this.sendMessage("ATZ");  
                    mConversationArrayAdapter.clear();    
                    MenuItem itemtemp = menu.findItem(R.id.menu_connect_scan);	     
           	        itemtemp.setTitle("DISCONNECT");           	       
                    break;
                case BluetoothService.STATE_CONNECTING:
                	 Status.setText(R.string.title_connecting);   
                	 tryconnect=true;
                    break;
                case BluetoothService.STATE_LISTEN:
                case BluetoothService.STATE_NONE: 
                	tryconnect=false;
                	Status.setText(R.string.title_not_connected);                	
                    break;
                }
                break;
            case MESSAGE_WRITE:
               byte[] writeBuf = (byte[]) msg.obj;
                // construct a string from the buffer
                String writeMessage = new String(writeBuf);
                mConversationArrayAdapter.add("Command:  " + writeMessage.trim());
                break;
            case MESSAGE_READ:
            	compileMessage( msg.obj.toString());              	  
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), "Connected to "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
                break;
            }
        }
    };
    private void settextsixe()
	{
		int txtsize=14;
		int txtsizelarge=18;
        int sttxtsize=12;         
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindow().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);     
           int densityDpi=displayMetrics.densityDpi;
	       if (densityDpi==DisplayMetrics.DENSITY_LOW) 
	       {		    	
        	 Status.setTextSize(txtsize);
        	 Fuel.setTextSize(txtsize);
        	 Temp.setTextSize(txtsize);
        	 Load.setTextSize(txtsize);
        	 Volt.setTextSize(txtsize);        	
        	 Temptext.setTextSize(txtsize);
        	 Loadtext.setTextSize(txtsize);
        	 Volttext.setTextSize(txtsize);          	 
             } 
             else if (densityDpi==DisplayMetrics.DENSITY_MEDIUM)
             {
        	 Status.setTextSize(sttxtsize*2);
        	 Fuel.setTextSize(txtsizelarge*2);
        	 Temp.setTextSize(txtsizelarge*2);
        	 Load.setTextSize(txtsizelarge*2);
        	 Volt.setTextSize(txtsizelarge*2);        	
        	 Temptext.setTextSize(txtsizelarge*2);
        	 Loadtext.setTextSize(txtsizelarge*2);
        	 Volttext.setTextSize(txtsizelarge*2); 
        	
             }
        	 else if (densityDpi==DisplayMetrics.DENSITY_HIGH)
        	 {
        	 Status.setTextSize(sttxtsize*3);
        	 Fuel.setTextSize(txtsize*4);
        	 Temp.setTextSize(txtsize*3);
        	 Load.setTextSize(txtsize*3);
        	 Volt.setTextSize(txtsize*3);        	
        	 Temptext.setTextSize(txtsize*3);
        	 Loadtext.setTextSize(txtsize*3);
        	 Volttext.setTextSize(txtsize*3);    
        	
        	 }
	}  
    private void compileMessage(String msg) {      
    	msg=msg.replace("null","");    	
    	msg = msg.substring(0,msg.length()-2);
    	msg = msg.replaceAll("\n", "");	
    	msg = msg.replaceAll("\r", "");	  
    	if(!initialized)
    	{ 
    		if(msg.contains("ELM327"))
        	{
    			msg =msg.replaceAll("ATZ", "");
    			msg =msg.replaceAll("ATI", "");
        		devicename=msg;	    		
        		getname=true;  	 
        		Status.setText(devicename); 
        	} 
        	if(msg.contains("ATDP"))
        	{
        		deviceprotocol=msg.replace("ATDP","");		
        		getprotocol=true;  	  
        		Status.setText(devicename + " " + deviceprotocol); 
        	} 
    		String send = initializeCommands[whichCommand];
			OBDActivity.this.sendMessage(send);   
			if(whichCommand == initializeCommands.length-1)
			{
				whichCommand = 0;
				initialized=true;		
				OBDActivity.this.sendMessage("ATRV"); 
			}
			else
			{
				whichCommand++;
			}    
    	}else
    	{ 
		int obdval=0;
	    tmpmsg="";
		if(commandmode)
		{
			int errors=0;
			String strerror="no error";
			if (msg.substring(0, 4).equals("4100")){
		
	            	 setPidsSupported(msg);	            	
	             }
			else if (msg.substring(0, 2).equals("43"))	
             {
             	try {	
             		if(deviceprotocol.contains("CAN"))
             		{
             			errors = Integer.parseInt(msg.substring(2, 4),16);
             			strerror=msg.substring(4, msg.length());
             		}else
             		{
             			strerror=msg.substring(2, msg.length());
             			errors=strerror.length()/4;
             		}
             		    mConversationArrayAdapter.add("Trouble Codes: " + errors + "\n\r"+strerror);    
	                } catch (NumberFormatException nFE) {
	                	mConversationArrayAdapter.add("Try Error:  " + msg);
	                }             	 
             }
			 else
    		 mConversationArrayAdapter.add(mConnectedDeviceName+":  " + msg);
		}
    	else{
    		try{    		   
	            if (msg.length() > 4)
	            {
	            	 /////mode 1//////////
	                if (msg.substring(0, 2).equals("41"))	
	                {
	                	try {		
	                	tmpmsg=msg.substring(0, 4);
	                	obdval = Integer.parseInt(msg.substring(4, msg.length()),16);
	                        } catch (NumberFormatException nFE) {}
	                if(tmpmsg.equals("410C"))
	     	             {
	     	             	//obdval = Integer.parseInt(msg.substring(4,8),16);
	     	                 int val = (int)(obdval / 4);
	     	                 rpmval=val;
	     	                 if(rpmval>8000)rpmval=8000;
	     	                 rpm.setTargetValue((int)(val)/100);			                
	     	             }
	   	            else if (tmpmsg.equals("410D"))
	     	             {	            	
	     	             	//obdval = Integer.parseInt(msg.substring(4,6),16);
	     	            	 speed.setTargetValue((int)obdval);		            	
	     	             }
	   	            else if (tmpmsg.equals("4105")){
	     	            	 int tempC = obdval - 40;
	     	            	 currenttemp=tempC;
	     	            	 Temp.setText(Integer.toString(tempC) + " C°");	
	     	             }	            
	   	            else if (tmpmsg.equals("410F")){
	    	            	 int tempC = obdval - 40;
	    	            	 intakeairtemp=tempC; 	 
	    	            	 Info.setText("Intakeairtemp: "+Integer.toString(intakeairtemp) + " C°");	
	    	             }
	   	            else if (tmpmsg.equals("4142")){
	   	            	contmodulevolt=obdval/1000;
	   	            	Info.setText("Contmodulevolt: "+Integer.toString(contmodulevolt) + " V");
	    	             }
	   	            else if (tmpmsg.equals("4146")){
	    	            	 int tempC = obdval - 40;
	    	            	 ambientairtemp=tempC; 	  
	    	            	 Info.setText("Ambientairtemp: "+Integer.toString(ambientairtemp) + " C°");	
	    	             }
	   	            else if (tmpmsg.equals("415C")){
	   	            	 int tempC = obdval - 40;
	   	            	 engineoiltemp=tempC;	
	   	            	 Info.setText("Engineoiltemp: "+Integer.toString(engineoiltemp) + " C°");
	   	             }
	   	            else if (tmpmsg.equals("413C")){
	   	            	 int tempC = (obdval/10) - 40;
	   	            	 b1s1temp=tempC;	
	   	            	 Info.setText("B1s1temp: "+Integer.toString(b1s1temp) + " C°");
	   	             }	   	          
	   	            else if (tmpmsg.contains("4104"))
	     	             {		            	
	     	            	 int calcLoad = obdval * 100 / 255; 
	     	            	 Load.setText(Integer.toString(calcLoad) + " %"); 
	     	            	 String avg = null;
	   		            	 if(Enginetype==0)
	   		            	 {
	   			            	 if(currenttemp<=55)
	   			            	 {
	   			            		 avg=String.format("%10.1f", (0.001*0.004*4*Enginedisplacement*rpmval*60*calcLoad/20)).trim();		
	   			            		 avgconsumption.add((0.001*0.004*4*Enginedisplacement*rpmval*60*calcLoad/20));
	   			            	 }
	   			            	 else if(currenttemp>55)
	   			            	 {
	   			            		 avg=String.format("%10.1f", (0.001*0.003*4*Enginedisplacement*rpmval*60*calcLoad/20)).trim();		
	   			            		 avgconsumption.add((0.001*0.003*4*Enginedisplacement*rpmval*60*calcLoad/20));
	   			            	 }
	   		            	 }else if(Enginetype==1)
	   		            	 {
	   		            		 if(currenttemp<=55)
	   		            		 {
	   		            			 avg=String.format("%10.1f", (0.001*0.004*4*1.35*Enginedisplacement*rpmval*60*calcLoad/20)).trim();
	   		            			 avgconsumption.add((0.001*0.004*4*1.35*Enginedisplacement*rpmval*60*calcLoad/20));
	   		            		 }
	   			            	 else if(currenttemp>55)
	   			            	 {
	   			            		 avg=String.format("%10.1f", (0.001*0.003*4*1.35*Enginedisplacement*rpmval*60*calcLoad/20)).trim();	
	   			            		 avgconsumption.add((0.001*0.003*4*1.35*Enginedisplacement*rpmval*60*calcLoad/20));
	   			            	 }
	   		            	 }        				            	
	   		            	 	Fuel.setText(avg  + " / " + String.format("%10.1f",calculateAverage(avgconsumption)).trim() + " L/h"); 		                 
	     	             }
	                 }	            	
	            }	           
	            else if (msg.indexOf("V") != -1)//battery voltage
                {
	            	 Volt.setText(msg);		            	 
                }
        			////commands/////////////    		
                	String send = commands[whichCommand];
    				OBDActivity.this.sendMessage(send);   
    				if(whichCommand >= commands.length-1)
    				{
    					whichCommand = 0;
    				}
    				else
    				{
    					whichCommand++;
    				}   
    		}catch(Exception e){}	
        	}    	
    	}
	}
    private void setPidsSupported(String buffer){
        byte[] pidSupported = null;
        StringBuilder flags = new StringBuilder();
        String buf = buffer.toString();
        buf = buf.trim();
        buf = buf.replace("\t", "");
        buf = buf.replace(" ", "");
        buf = buf.replace(">", "");
        pidSupported = buf.getBytes();
        if(buf.indexOf("4100") == 0){
            for(int i = 0; i < 8; i++ ){
                String tmp = buf.substring(i+4, i+5);
                int data = Integer.valueOf(tmp, 16).intValue();
//                String retStr = Integer.toBinaryString(data);
                if ((data & 0x08) == 0x08){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }

                if ((data  & 0x04) == 0x04){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }

                if ((data  & 0x02) == 0x02){
                    flags.append("1");
                }else{
                    flags.append( "0");
                 }

                if ((data  & 0x01) == 0x01){
                    flags.append("1");
                }else{
                    flags.append( "0");
                }
            }

            StringBuilder supportedPID = new StringBuilder();
            supportedPID.append("SupportedPID: ");
            StringBuilder unSupportedPID = new StringBuilder();
            unSupportedPID.append("UnSupportedPID: ");
            for(int j = 0; j < flags.length(); j++){
                if(flags.charAt(j) == '1'){
                    supportedPID.append(" "+ PIDS[j] + " ");
                }else{
                    unSupportedPID.append(" "+ PIDS[j] + " ");
                }
            }
            supportedPID.append("\n");
            mConversationArrayAdapter.add(supportedPID.toString() + unSupportedPID.toString());            
        }else{
            return;
        }
    }

    private double calculateAverage(ArrayList<Double> listavg) {
		  Double sum = 0.0;
		  for (Double val : listavg) {
		      sum += val;
		  }
		  return sum.doubleValue() / listavg.size();
		}
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + resultCode);
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
            	getname=false;
                connectDevice(data);
            }
            break;
        case REQUEST_ENABLE_BT:           
            if (resultCode == Activity.RESULT_OK) {                
            	if (mChatService == null) setupChat();
            } else {               
                Toast.makeText(this, "BT not enabled", Toast.LENGTH_SHORT).show();      
                if (mChatService == null) setupChat();
            }
        }
    }

    private void connectDevice(Intent data) {    	
        // Get the device MAC address
        String address = data.getExtras()
            .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        getname=false;getprotocol=false;commandmode=false;getecho=false;initialized=false;setprotocol=false;
        whichCommand=0;  
        
        mChatService.connect(device);
    }
    private Menu menu;
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	this.menu = menu;
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }   
    private void hideVirturalKeyboard(){
    	try{
    		InputMethodManager inputManager = (InputMethodManager)            
    				  this.getSystemService(Context.INPUT_METHOD_SERVICE); 
    				    inputManager.hideSoftInputFromWindow(this.getCurrentFocus().getWindowToken(),      
    				    InputMethodManager.HIDE_NOT_ALWAYS);
    	}catch(Exception e){}
    } 
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;     
        hideVirturalKeyboard();
        switch (item.getItemId()) {
        case R.id.menu_connect_scan:        	
        	if(item.getTitle().equals("CONNECT"))
        	{
        		// Launch the DeviceListActivity to see devices and do scan
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);	        	
        	}else
        	{
        		if (mChatService != null) mChatService.stop();
        		item.setTitle("CONNECT");    
        		Status.setText("Not Connected"); 
        		resetvalues();
        		 if(gps)
        		    {     			    
        			    if(gps_enabled)
        				{				
        					initGps();		 			
        				}
        		    }
        	}
          
            return true;
        case R.id.menu_command:           	
        	mConversationArrayAdapter.clear();   
        	if(item.getTitle().equals("SETPID"))
        	{
        		commandmode=true;
	        	visiblecmd();
	        	item.setTitle("GAUGES");
        	}else
        	{
        		invisiblecmd();
        		item.setTitle("SETPID");
        		commandmode=false;  
        		OBDActivity.this.sendMessage("ATRV"); 
        	}
            return true;
       
        case R.id.menu_settings:        	
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, Prefs.class);
            startActivity(serverIntent);
            return true;      
        case R.id.menu_exit:  
        	 if (mChatService != null) mChatService.stop();
             try
     			{
         		if (locationManager != null)
         		{
         		locationManager.removeGpsStatusListener(gpsStatusListener);
         		locationManager.removeUpdates(locationListener);                		
         		}			
     			}catch(Exception e){}      
             wl.release();	
        	android.os.Process.killProcess(android.os.Process.myPid());
            return true;      
	    case R.id.menu_reset:
	    	 getname=false;getprotocol=false;commandmode=false;getecho=false;setprotocol=false;
	         whichCommand=0;  
	         invisiblecmd();
	 		 commandmode=false;		     
		     MenuItem itemtemp = menu.findItem(R.id.menu_command);	     
		     itemtemp.setTitle("SETPID");	
		     resetvalues();
		     OBDActivity.this.sendMessage("ATZ");
	     return true;       
	    }
        return false;
    }
    private String hexToBin(String hex){
        String bin = "";
        String binFragment = "";
        int iHex;
        hex = hex.trim();
        hex = hex.replaceFirst("0x", "");

        for(int i = 0; i < hex.length(); i++){
            iHex = Integer.parseInt(""+hex.charAt(i),16);
            binFragment = Integer.toBinaryString(iHex);

            while(binFragment.length() < 4){
                binFragment = "0" + binFragment;
            }
            bin += binFragment;
        }
        return bin;
    }
}
