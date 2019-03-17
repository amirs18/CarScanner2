package com.example.carscanner;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

import antonkozyriatskyi.circularprogressindicator.CircularProgressIndicator;

public class MainActivity extends AppCompatActivity {
    public static int var = 1;
    private static String KEY_temp = "1";
    boolean n = true;
    Handler handler1;
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String deviceAddress;
    public float max_temo = 0;
    TextView txt;
    TextView lan;
    TextView lon;
    CircularProgressIndicator circularProgressIndicator;
    CircularProgressIndicator circularProgressIndicator1;
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        break;
                    case BluetoothAdapter.STATE_ON:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        circularProgressIndicator = findViewById(R.id.circular_progress);
        circularProgressIndicator1 =findViewById(R.id.circular_progress1);
        ArrayList deviceStrs = new ArrayList();
        txt = findViewById(R.id.txt);
        lon = findViewById(R.id.lon);
        lan = findViewById(R.id.lan);
        final ArrayList devices = new ArrayList();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);

        registerReceiver(mReceiver, filter);
        final BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            // set up list selection handlers
            for (BluetoothDevice device : pairedDevices) {
                deviceStrs.add(device.getName() + "\n" + device.getAddress());
                devices.add(device.getAddress());
            }

            final AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);

            ArrayAdapter adapter = new ArrayAdapter(this, android.R.layout.select_dialog_singlechoice,
                    deviceStrs.toArray(new String[deviceStrs.size()]));

            alertDialog.setSingleChoiceItems(adapter, -1, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                    int position = ((AlertDialog) dialog).getListView().getCheckedItemPosition();
                    String deviceAddress = (String) devices.get(position);
                    // TODO save deviceAddress

                    BluetoothDevice device = btAdapter.getRemoteDevice(deviceAddress);
                    livedata livedata = new livedata(handler1, device);
                    livedata.start();
                }
            });

            alertDialog.setTitle("Choose Bluetooth device");
            alertDialog.show();
            handler1 = new Handler(new Handler.Callback() {
                @Override
                public boolean handleMessage(Message msg) {
                    txt.setText(msg.getData().getString("string temp"));
                    circularProgressIndicator.setProgress(msg.getData().getInt("int rpm,"), 10000);
                    //circularProgressIndicator1.setProgress(msg.getData().getInt("int speed"),200);
                    circularProgressIndicator1.setProgress(100,200);
                    int max_speed=0;
                    max_speed=Math.max(max_speed,msg.getData().getInt("int speed"));
                    if (n && msg.getData().getFloat("float temp") > 95) {
                        addNotification();
                        n = false;
                    }
                    return true;
                }
            });

        }

        MyLocation myLocation = new MyLocation();
       myLocation.getLocation();
    }

    private static BluetoothSocket connect(BluetoothDevice dev) throws IOException {
        int i = 0;
        BluetoothSocket sock = null;
        BluetoothSocket sockFallback;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothAdapter.cancelDiscovery();

        Log.d("myee", "Starting Bluetooth connection..");

        try {
            sock = dev.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            sock.connect();
        } catch (Exception e1) {
            Log.e("myee", "There was an error while establishing Bluetooth connection. Falling back..", e1);
            if (sock != null) {
                Class<?> clazz = sock.getRemoteDevice().getClass();
                Class<?>[] paramTypes = new Class<?>[]{Integer.TYPE};
                try {
                    Log.e("", "trying fallback...");

                    sock = (BluetoothSocket) dev.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(dev, 1);
                    sock.connect();

                    Log.e("myee", "Connected");
                } catch (Exception e2) {
                    Log.e("myee", "Couldn't establish Bluetooth connection!");
                }
            }
        }


        return sock;
    }

    class livedata extends Thread {
        BluetoothSocket socket = null;
        Handler handler;
        BluetoothDevice device;

        public livedata(Handler handler1, BluetoothDevice device) {
            this.handler = handler1;
            this.device = device;
        }

        @Override
        public void run() {
            super.run();
            String tmp;

            try {

                socket = connect(device);
                try {
                    new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new TimeoutCommand(125).run(socket.getInputStream(), socket.getOutputStream());
                    new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());

                } catch (Exception e) {
                    Log.d("myee", e + "");
                    // handle errors
                }
            } catch (IOException e) {
                Log.d("myee", e.getMessage());
            }


            while (true) {
                EngineCoolantTemperatureCommand r = new EngineCoolantTemperatureCommand();
                RPMCommand rpm =new RPMCommand();
                SpeedCommand speedCommand=new SpeedCommand();
                try {
                    speedCommand.run(socket.getInputStream(),socket.getOutputStream());
                    r.run(socket.getInputStream(), socket.getOutputStream());
                    rpm.run(socket.getInputStream(),socket.getOutputStream());
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                Message message = new Message();
                Bundle bundle = new Bundle();
                bundle.putFloat("float temp", r.getTemperature());
                bundle.putString("string temp", r.getFormattedResult());
                bundle.putInt("int rpm",rpm.getRPM());
                bundle.putInt("int speed",speedCommand.getMetricSpeed());
                message.setData(bundle);
                handler.sendMessage(message);
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addNotification() {
        // Builds your notification
        createNotificationChannel();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "chanel")
                .setSmallIcon(R.drawable.example_picture)
                .setContentTitle("CarScanner")
                .setContentText("your car is overheating");

        // Creates the intent needed to show the notification
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(contentIntent);

        // Add as notification
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(101, builder.build());
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("chanel", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    class MyLocation {
        LocationManager locationManager;
        Location location;
        LocationListener locationListener;

        public MyLocation() {

        }


       public void getper(){
            boolean x=checkper();
           while (x){
               ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 1);
                x = checkper();
           }

       }
        public boolean checkper(){
            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
            return false;
        }

        public void getLocation() {
            getper();
            lan.setText("0");
            lon.setText("0");
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            int currentapiVersion = Build.VERSION.SDK_INT;

            if (currentapiVersion >= Build.VERSION_CODES.HONEYCOMB) {

                criteria.setSpeedAccuracy(Criteria.ACCURACY_HIGH);
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                criteria.setAltitudeRequired(true);
                criteria.setBearingRequired(true);
                criteria.setSpeedRequired(true);
                String provider = locationManager.getBestProvider(criteria, true);
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    location = locationManager.getLastKnownLocation(provider);
                    lan.setText(location.getLatitude()+" ");
                    lon.setText(location.getLongitude()+" ");

                }

            }


        }

    }
}