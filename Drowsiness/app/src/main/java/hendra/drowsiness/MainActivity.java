package hendra.drowsiness;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.support.constraint.ConstraintLayout;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GraphView.GraphViewData;
import com.jjoe64.graphview.GraphViewSeries;
import com.jjoe64.graphview.LineGraphView;
import com.jjoe64.graphview.GraphViewStyle;

import com.firebase.client.Firebase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Calendar;

import hrv.RRData;
import hrv.calc.parameter.HRVParameter;
import units.TimeUnit;

public class MainActivity extends Activity implements View.OnClickListener {
    @Override
    public void onBackPressed(){
        new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_dialog_alert).setTitle("Exit")
                .setMessage("Are you sure?")
                .setPositiveButton("yes", new DialogInterface.OnClickListener(){
                    @Override
                    public  void onClick(DialogInterface dialog, int which){
                        Intent intent = new Intent(Intent.ACTION_MAIN);
                        intent.addCategory(Intent.CATEGORY_HOME);
                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(intent);
                        finish();
                        System.exit(0);
                    }
                }).setNegativeButton("no", null).show();
    }

    Boolean bltConnect = false;

    int initRR       = 0;
    double lengthRR  = 10;
    double thresholdBottom = 0.23;
    double thresholdUp     = 0.30;
    double[] rrData = {0.00, 0.00, 0.00, 0.00,0.00, 0.00, 0.00, 0.00, 0.00, 0.00};
    double[] rrTime = {0.00, 0.00, 0.00, 0.00,0.00, 0.00, 0.00, 0.00, 0.00, 0.00};

    private Vibrator vib;
    private MediaPlayer mp;

    double theHF, theLF, LFHF;

    //GraphView Init
    private ConstraintLayout GraphView;
    private GraphView graphView;
    private GraphViewSeries Series;

    //Graph Value
    private static double graph2LastXValue = 0;
    private static int Xview = 500;

    //toggle Button
    TextView QRS;
    static boolean Lock;
    static boolean AutoScrollX;
    static boolean Stream;

    static boolean startRecording = false;
    static boolean stopRecording = false;
    static boolean detectDrowsy = false;
    String strIncomData = "0";
    String strIncomRate = "100";

    //Button Init
    Button bXminus,bXplus,bLoad, bBrowse, bConnect, bDisconnect, bFolder;
    ToggleButton tbLock, tbScroll, tbRecord, tbDrowsiness;
    EditText myfileName;
    TextView textLF,textHF, textLFHF;

    private final int REQUEST_CODE_PICK_DIR = 1;
    private final int REQUEST_CODE_PICK_FILE = 2;
    String newDir   = "";
    String newFile  = "";
    String fileName = "";

    private int QRSVal;
    private double last_defHR;
    private String voltageVal, printFormat, heartRate, dataName;
    private String observeItem = "0.000";
    double startTime = 0.00000;
    double startRR   = 0.00000;

    @SuppressLint("HandlerLeak")
    Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case BluetoothActivity.SUCCESS_CONNECT:
                    BluetoothActivity.connectedThread = new BluetoothActivity.ConnectedThread((BluetoothSocket)msg.obj);
                    Toast.makeText(getApplicationContext(),"Connected!",Toast.LENGTH_SHORT).show();
                    bltConnect = true;
                    String s = "Successfully  Connected";
                    BluetoothActivity.connectedThread.start();
                    startRR = System.currentTimeMillis()/1000.00000;
                    break;
                case BluetoothActivity.MESSAGE_READ:
                    byte[] readBuf = (byte[])msg.obj;
                    int i = 0;
                    for (i = 0; i < readBuf.length && readBuf[i] != 0; i++) {
                    }
                    final String Income = new String(readBuf,0,i);
                    String[] items = Income.split("\\*");
                    for(String item : items){
                        if(item.length() == 3){
                            observeItem = item;
                            voltageValue(item);
                            item = voltageVal;
                            graphIt(item);
                            saveIt(item, heartRate);
                            strIncomData = item;
                        }
                        else if(item.length() == 4){
                            if(initRR < lengthRR){
                                double curTime  = System.currentTimeMillis()/1000.00000;
                                double time     = curTime - startRR ;
                                time            = round(time,2);
                                rrTime[initRR]  = time;
                                rrData[initRR]  = Double.parseDouble(item)/1000.00;

                            }else{
                                double curTime  = System.currentTimeMillis()/1000.00000;
                                double newTime  = curTime - startRR ;
                                newTime         = round(newTime,2);
                                double newRR    = Double.parseDouble(item)/1000.00;
                                rrData          = shiftData(rrData,newRR);
                                rrTime          = shiftData(rrTime,newTime);

                                Log.d("RR is:", Arrays.toString(rrData));
                                Log.d("Time is:", Arrays.toString(rrTime));
                                calcLFHF(rrData,rrTime);
                                String sHF   = "HF: " + String.valueOf(theHF);
                                String sLF   = "LF: " + String.valueOf(theLF);
                                String sLFHF = "LF/HF: " + String.valueOf(LFHF);

                                if(LFHF > 0.1){
                                    textHF.setText(sHF);
                                    textLF.setText(sLF);
                                    textLFHF.setText(sLFHF);
                                }
                            }
                            initRR = initRR +1;
                            double rrVal  = Double.parseDouble(item);
                            double BPM    = (60.00*1000.00)/rrVal;
                            int HR        = (int)BPM;
                            heartRate     = String.valueOf(HR);
                            strIncomRate  = item;
                            String sHR   = "HR: " + heartRate + " bpm";
                            QRS.setText(sHR);

                            if (BPM > 100.00 || BPM < 60.00) {
                                QRS.setTextColor(Color.RED);
                            } else QRS.setTextColor(Color.BLACK);

                            if(detectDrowsy) {
                                if ((LFHF > thresholdBottom && LFHF < thresholdUp) || BPM < 60.00) {
                                    vib.vibrate(3000);
                                    mp.start();
                                }
                            }
                        }
                    }
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        LinearLayout background = (LinearLayout) findViewById(R.id.activity_main);
        background.setBackgroundColor(Color.BLACK);
        Firebase.setAndroidContext(this);
        initGraph();
        buttonInit();

        mp = MediaPlayer.create(this,R.raw.beep);
        vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

    }

    void initGraph(){
        BluetoothActivity.gethandler(mHandler);

        //Init GraphView
        GraphView = (ConstraintLayout) findViewById(R.id.Graph);

        //Init Example Series Data

        Series = new GraphViewSeries("Signal",
                new GraphViewSeries.GraphViewSeriesStyle(Color.YELLOW,2),
                new GraphViewData[]{new GraphViewData(0,0)});

        graphView = new LineGraphView(this, "");

        graphView.setViewPort(0, Xview);
        graphView.setScrollable(true);
        graphView.setScalable(true);
        graphView.setManualYAxis(true);
        graphView.setManualYAxisBounds(3.00017972, 0.002932727);
        graphView.addSeries(Series); //Data
        GraphView.addView(graphView);
        graphView.setHorizontalLabels(new String[] {"","", "", "", "","","","","","",""});
        graphView.setVerticalLabels(new String[] {"","", "", "", "","","","","","",""});
    }

    public void buttonInit(){
        bConnect = (Button)findViewById(R.id.bConnect);
        bConnect.setOnClickListener(this);
        bDisconnect = (Button)findViewById(R.id.bDisconnect);
        bDisconnect.setOnClickListener(this);
        bXminus = (Button)findViewById(R.id.bXminus);
        bXminus.setOnClickListener(this);
        bXplus = (Button)findViewById(R.id.bXplus);
        bXplus.setOnClickListener(this);
        tbLock = (ToggleButton)findViewById(R.id.tbLock);
        tbLock.setOnClickListener(this);
        tbScroll = (ToggleButton)findViewById(R.id.tbScroll);
        tbScroll.setOnClickListener(this);
        tbRecord = (ToggleButton)findViewById(R.id.tbRecord);
        tbRecord.setOnClickListener(this);
        bFolder = (Button)findViewById(R.id.bFolder);
        bFolder.setOnClickListener(this);
        tbDrowsiness = (ToggleButton)findViewById(R.id.tbDrowsiness);
        tbDrowsiness.setOnClickListener(this);
        bLoad = (Button)findViewById(R.id.bLoad);
        bLoad.setOnClickListener(this);
        bBrowse = (Button) findViewById(R.id.bBrowse);
        bBrowse.setOnClickListener(this);
        QRS = (TextView) findViewById(R.id.QRS);
        QRS.setMovementMethod(new ScrollingMovementMethod());

        textHF = (TextView) findViewById(R.id.HF);
        textHF.setMovementMethod(new ScrollingMovementMethod());
        textLF = (TextView) findViewById(R.id.LF);
        textLF.setMovementMethod(new ScrollingMovementMethod());
        textLFHF = (TextView) findViewById(R.id.LFHF);
        textLFHF.setMovementMethod(new ScrollingMovementMethod());

        myfileName = (EditText) findViewById(R.id.myfileName);
        Lock = true;
        AutoScrollX = true;
        Stream = true;
    }

    public void graphIt(String inputString){
        Series.appendData(new GraphViewData(graph2LastXValue, Double.parseDouble(inputString)),AutoScrollX,1000);
        if (graph2LastXValue >= Xview && Lock) {
            graph2LastXValue = 0;
            Series.resetData(new GraphViewData[]{});
        } else {graph2LastXValue += 0.5;}

        if (Lock)
            graphView.setViewPort(0, Xview);
        else
            graphView.setViewPort(graph2LastXValue - Xview, Xview);
        //Refresh
        GraphView.removeView(graphView);
        GraphView.addView(graphView);
    }

    public void saveIt(String saveString, String saveHR){
        if (startRecording) {
            try {
                double nowTime  = System.currentTimeMillis()/1000.00000;
                double time     = nowTime - startTime ;
                double recItem  = Double.parseDouble(saveString);
                double defHR    = Double.parseDouble(saveHR);

                if(defHR == last_defHR || observeItem.length() == 3) {
                    defHR = 0;
                    printFormat = String.format("%.5f\t%.3f\t%.0f", time, recItem, defHR);
                }else{
                    printFormat = String.format("%.5f\t%.3f\t%.0f", time, recItem, defHR);
                    last_defHR  = defHR;
                }

                FileWriter fw = new FileWriter(fileName, true);
                fw.append(printFormat+"\n");
                fw.flush();
                fw.close();
            } catch (IOException e) {
            }
        }
    }

    public void browseFolder(){
        final Activity activityForButton = this;
        Log.d("Activity", "Start Browsing");
        Intent fileExplorerIntent = new Intent(hendra.drowsiness.FileBrowserActivity.INTENT_ACTION_SELECT_DIR,null,
                activityForButton,
                hendra.drowsiness.FileBrowserActivity.class
        );
        startActivityForResult(
                fileExplorerIntent,
                REQUEST_CODE_PICK_DIR
        );
    }

    public void browseFile(){
        final Activity activityForButton = this;
        Log.d("Activity", "Start Browsing");
        Intent fileExplorerIntent = new Intent(hendra.drowsiness.FileBrowserActivity.INTENT_ACTION_SELECT_FILE,null,
                activityForButton,
                hendra.drowsiness.FileBrowserActivity.class
        );
        startActivityForResult(
                fileExplorerIntent,
                REQUEST_CODE_PICK_FILE
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if(requestCode == REQUEST_CODE_PICK_DIR){
            if(resultCode == RESULT_OK){
                newDir = data.getStringExtra(FileBrowserActivity.returnDirectoryParameter);
                Toast.makeText(this, "Received FILE path from file browser:\n"+newDir, Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"Received NO result from file browser", Toast.LENGTH_LONG).show();
            }
        }

        if(requestCode == REQUEST_CODE_PICK_FILE){
            if(resultCode == RESULT_OK){
                newFile = data.getStringExtra(FileBrowserActivity.returnFileParameter);
                Toast.makeText(this, "Received FILE path from file browser:\n"+newFile, Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this,"Received NO result from file browser", Toast.LENGTH_LONG).show();
            }
        }

        super.onActivityResult(requestCode,resultCode,data);
    }

    public void openFile(){
        Log.d("part", newFile);
        if (newFile.indexOf('/')==0){
            File file = new File (newFile);
            String [] loadText = Load(file);
            int dataLength;
            int i = 0;
            if(loadText.length>1000){
                dataLength = 1000;
            }else{
                dataLength = loadText.length;
            }
            while(i < dataLength){
                loadText[i] = loadText[i].replace(",",".");
                graphIt(loadText[i]);
                i++;
            }
            Toast.makeText(this, "Done! Touch your screen.", Toast.LENGTH_LONG).show();
            newFile = "";
        }else Toast.makeText(this, "Please, select your file!", Toast.LENGTH_LONG).show();
    }

    public static String[] Load(File file) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        InputStreamReader isr = new InputStreamReader(fis);
        BufferedReader br = new BufferedReader(isr);

        int dataLength=0;
        try {
            while ((br.readLine()) != null) {
                dataLength++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            fis.getChannel().position(0);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String sCurrentLine;
        String[] characters = new String[dataLength];
        int i = 0;
        try {
            while ((sCurrentLine = br.readLine())!=null) {
                String[] arr = sCurrentLine.split("\t");
                characters[i] = arr[1];
                i++;
            }
        } catch (IOException e) {e.printStackTrace();}
        return characters;
    }

    public void voltageValue(String object){
        Double voltage = Double.parseDouble(object);
        voltage = 3.226 * (voltage/1100);
        voltageVal = String.format("%.2f",voltage);
        voltageVal = voltageVal.replace(",",".");
    }

    public void calcLFHF(double[] RRdata, double[] RRtime) {

        RRData rr = new RRData(RRtime, TimeUnit.SECOND, RRdata, TimeUnit.SECOND);

        HRVCalculatorFacade controller = new HRVCalculatorFacade(rr);
        HRVParameter HF = controller.getHF();
        theHF = Math.abs(HF.getValue());
        theHF = round(theHF,2);

        HRVParameter LF = controller.getLF();
        theLF = Math.abs(LF.getValue());
        theLF = round(theLF,2);
        LFHF = Math.abs(theLF/theHF);
        LFHF = round(LFHF,2);

        Log.d("HF is:",Double.toString(theHF));
        Log.d("LF is:",Double.toString(theLF));
        Log.d("LF/HF is:",Double.toString(LFHF));
    }

    public double[] shiftData(double[] array,double newArray) {
        for (int j = 0; j < 1; j++) {
            double a = newArray;
            int i;
            for (i = 0; i < array.length - 1; i++)
                array[i] = array[i + 1];
            array[i] = a;
        }
        return array;
    }

    public static double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bConnect:
                startActivity(new Intent("android.intent.action.BT1"));
                break;

            case R.id.bDisconnect:
                BluetoothActivity.disconnect();
                mp.stop();
                vib.cancel();
                break;

            case R.id.bXminus:
                if (Xview > 0) Xview = Xview - 50;
                break;

            case R.id.bXplus:
                if (Xview < 1000) Xview = Xview + 50;
                break;

            case R.id.tbLock:
                if (tbLock.isChecked()) {
                    Lock = true;
                } else {
                    Lock = false;
                }
                break;

            case R.id.tbScroll:
                if (tbScroll.isChecked()) {
                    AutoScrollX = true;
                } else {
                    AutoScrollX = false;
                }
                break;

            case R.id.tbRecord:
                if (tbRecord.isChecked()) {
                    if (newDir.indexOf('/')==0){
                        Calendar now = Calendar.getInstance();

                        //Get Time
                        String month = String.valueOf(now.get(Calendar.MONTH)+1);
                        String day = String.valueOf(now.get(Calendar.DAY_OF_MONTH));
                        String hour = String.valueOf(now.get(Calendar.HOUR_OF_DAY));
                        String minute = String.valueOf(now.get(Calendar.MINUTE));
                        startRecording = true;
                        stopRecording = false;

                        File root = new File(newDir);
                        if (!root.exists()) {
                            root.mkdirs();
                        }

                        dataName = myfileName.getText().toString();

                        if(dataName.matches("")){
                            dataName = "DataEKG";
                        }

                        Toast.makeText(this, "Your data name: "+dataName, Toast.LENGTH_SHORT).show();

                        fileName = newDir+"/"+dataName+day+month+hour+minute+".txt";
                        startTime= System.currentTimeMillis()/1000.000;

                    }else {
                        Toast.makeText(this, "Please, select your folder!", Toast.LENGTH_SHORT).show();
                        tbRecord.setChecked(false);
                    }
                } else {
                    try{
                        stopRecording = true;
                        startRecording = false;
                        Toast.makeText(this, "Stop Recording!", Toast.LENGTH_SHORT).show();
                        newDir = "";
                    }catch (Exception e){
                        Toast.makeText(getBaseContext(), e.getMessage(),Toast.LENGTH_SHORT).show();
                    }
                }
                break;

            case R.id.bFolder:
                browseFolder();
                break;

            case R.id.tbDrowsiness:
                if (tbDrowsiness.isChecked()) {
                    detectDrowsy = true;
                } else {
                    detectDrowsy = false;
                    mp.stop();
                }
                break;

            case R.id.bLoad:
                Lock = false;
                AutoScrollX = false;
                tbLock.setChecked(false);
                tbScroll.setChecked(false);
                openFile();
                break;

            case R.id.bBrowse:
                browseFile();
                BluetoothActivity.disconnect();
                break;
        }
    }
}
