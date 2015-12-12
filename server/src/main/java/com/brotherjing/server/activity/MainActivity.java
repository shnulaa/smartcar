package com.brotherjing.server.activity;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.brotherjing.server.CONSTANT;
import com.brotherjing.server.GlobalEnv;
import com.brotherjing.server.R;
import com.brotherjing.server.controller.BluetoothCarController;
import com.brotherjing.server.service.BluetoothService;
import com.brotherjing.server.service.TCPServer;
import com.brotherjing.utils.Logger;
import com.brotherjing.utils.Protocol;
import com.brotherjing.utils.bean.CommandMessage;
import com.brotherjing.utils.bean.TextMessage;
import com.google.gson.Gson;


public class MainActivity extends ActionBarActivity {

    final static int REQ_BLUETOOTH = 1;
    //SectionsPagerAdapter mSectionsPagerAdapter;
    //List<Fragment> fragments;
    int currentIndex;
    boolean isBluetoothServiceBinded = false;

    //ViewPager mViewPager;
    TextView tv_addr,tv_content;
    Button mButton, qrCodeButton,btnBluetooth,btnCarCommand;

    MainThreadHandler handler;
    MainThreadReceiver receiver;
    TCPServer.MyBinder binder;
    BluetoothService.MyBinder bluetoothBinder;
    BluetoothCarController carController = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        //register broadcast listening to server event
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(CONSTANT.ACTION_SERVER_UP);
        intentFilter.addAction(CONSTANT.ACTION_NEW_MSG);
        receiver = new MainThreadReceiver();
        registerReceiver(receiver,intentFilter);

        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        //initFragments();
        tv_addr = (TextView)findViewById(R.id.tv_ipaddr);
        tv_content = (TextView)findViewById(R.id.tv_content);
        mButton = (Button) findViewById(R.id.button_capture_image);
        qrCodeButton = (Button) findViewById(R.id.btn_generate_qrcode);
        btnBluetooth = (Button) findViewById(R.id.btn_bluetooth);
        btnCarCommand = (Button) findViewById(R.id.btn_cmd);

        initData();
    }

    private void initData(){
        handler = new MainThreadHandler(this);

        mButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                //Intent intent = new Intent(MainActivity.this, VideoActivity.class);
                Intent intent = new Intent(MainActivity.this,SimpleVideoActivity.class);
                startActivity(intent);
//                if (!binder.getClientSockets().isEmpty()){
//                    Toast.makeText(MainActivity.this, "has" ,Toast.LENGTH_SHORT).show();
//                } else {
//                    Toast.makeText(MainActivity.this, "none" ,Toast.LENGTH_SHORT).show();
//                }
            }
        });

        qrCodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, QrcodeActivity.class);
                startActivity(intent);
            }
        });

        btnBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(new Intent(MainActivity.this, BluetoothActivity.class), REQ_BLUETOOTH);
            }
        });

        btnCarCommand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothBinder.send("1");
            }
        });
    }

    public final static class MainThreadHandler extends Handler{
        private WeakReference<MainActivity> reference;
        public MainThreadHandler(MainActivity activity) {
            super();
            reference = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            MainActivity activity = reference.get();
            switch (msg.what){
                case CONSTANT.MSG_IP_ADDR:
                    String ip = msg.getData().getString(CONSTANT.KEY_IP_ADDR);
                    GlobalEnv.put(CONSTANT.GLOBAL_IP_ADDRESS,ip);
                    /*Fragment currentFragment = activity.fragments.get(activity.currentIndex);
                    if(currentFragment instanceof ServerFragment){
                        ((ServerFragment) currentFragment).refreshIpAddr(ip);
                    }*/
                    activity.tv_addr.setText(ip);
                    break;
                case CONSTANT.MSG_NEW_MSG:
                    com.brotherjing.utils.bean.Message message = new Gson().fromJson(msg.getData().getString(CONSTANT.KEY_MSG_DATA), com.brotherjing.utils.bean.Message.class);
                    if(message.getMsgType()== Protocol.MSG_TYPE_TEXT){
                        TextMessage textMessage = new Gson().fromJson(msg.getData().getString(CONSTANT.KEY_MSG_DATA),TextMessage.class);
                        Logger.i(msg.getData().getString(CONSTANT.KEY_MSG_DATA));
                    /*Fragment cf = activity.fragments.get(activity.currentIndex);
                    if(cf instanceof ServerFragment){
                        Logger.i("is server fragment");
                        ((ServerFragment) cf).newMessage(textMessage);
                    }*/
                        activity.tv_content.setText(textMessage.getText()+"\n"+activity.tv_content.getText().toString());
                    }else{
                        CommandMessage cmd = new Gson().fromJson(msg.getData().getString(CONSTANT.KEY_MSG_DATA),CommandMessage.class);
                        if(activity.carController!=null){
                            activity.carController.processCommand(cmd.getCommand());
                        }
                    }

                    break;
                default:break;
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindService(new Intent(this, TCPServer.class), conn, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.i("stop");
        unbindService(conn);
        if(isBluetoothServiceBinded)
            unbindService(bluetoothConn);
        //handler = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Logger.i("destroy");
        handler = null;
        //stopService(new Intent(this,TCPServer.class));
        unregisterReceiver(receiver);
    }


    //broadcast receiver listening to server events
    private class MainThreadReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            //if server is up and run, it will send ip address back
            Message msg = handler.obtainMessage();
            Bundle bundle = new Bundle();
            if(intent.getAction().equals(CONSTANT.ACTION_SERVER_UP)){
                msg.what = CONSTANT.MSG_IP_ADDR;
                bundle.putString(CONSTANT.KEY_IP_ADDR, intent.getStringExtra(CONSTANT.KEY_IP_ADDR));
            }
            else if(intent.getAction().equals(CONSTANT.ACTION_NEW_MSG)){
                msg.what=CONSTANT.MSG_NEW_MSG;
                bundle.putString(CONSTANT.KEY_MSG_DATA, intent.getStringExtra(CONSTANT.KEY_MSG_DATA));
            }else{
                return;
            }
            msg.setData(bundle);
            msg.sendToTarget();
        }
    }

    private ServiceConnection conn = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            //when server is bonded, request the ip address
            binder = (TCPServer.MyBinder)iBinder;
            String ip;
            if((ip=binder.getIP())!=null){
                Message msg = new Message();
                Bundle bundle = new Bundle();
                bundle.putInt(CONSTANT.KEY_MSG_TYPE,CONSTANT.MSG_IP_ADDR);
                bundle.putString(CONSTANT.KEY_IP_ADDR, ip);
                msg.setData(bundle);
                handler.sendMessage(msg);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {

        }
    };

    private ServiceConnection bluetoothConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            bluetoothBinder = (BluetoothService.MyBinder)service;
            carController = new BluetoothCarController(bluetoothBinder);
            Toast.makeText(MainActivity.this,"get bluetooth binder",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==REQ_BLUETOOTH){
            if(resultCode==RESULT_OK){
                isBluetoothServiceBinded = true;
                BluetoothDevice device = data.getParcelableExtra(CONSTANT.KEY_DEVICE);
                Toast.makeText(this,device.getName(),Toast.LENGTH_SHORT).show();
                bindService(new Intent(this,BluetoothService.class),bluetoothConn,BIND_AUTO_CREATE);
            }
        }
    }
}
