package com.wyt.serviceaidldemo;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.zhy.m.permission.MPermissions;
import com.zhy.m.permission.PermissionDenied;
import com.zhy.m.permission.PermissionGrant;

import java.util.ArrayList;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    private Button bindService, unbindService, download_again;

    private MyAIDLService myAIDLService = null;

    //用于判断跳转Intent类型
    private static final int REQUECT_CODE_SDCARD = 1;

    //广播接收器
    private MsgReceiver msgReceiver;

    private Message message = new Message();

    //测试链接
    private ArrayList<String> URLS = new ArrayList<>();
    private String URL = "http://uc1-apk.wdjcdn.com/1/59/321568928eef09d7b8f653c760475591.apk";
    private String URL1 = "http://issuecdn.baidupcs.com/issue/netdisk/apk/BaiduYun_7.13.3.apk";

    //下载标识
    private static final int DOWNLOADPREPARE = 0x00;//准备下载
    private static final int DOWNLOADING = 0x01;//下载中
    private static final int DOWNLOADSUCCESS = 0x02;//下载完成
    private static final int DOWNLOADFAILE = 0x03;//下载失败

    //连接服务
    private ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myAIDLService = MyAIDLService.Stub.asInterface(service);
            //服务绑定后开始下载
            handler.sendMessage(message);
        }
    };

    /**
     * 服务绑定后开始下载
     */
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            try {
                myAIDLService.download(msg.obj.toString());
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        URLS.add(URL);
        URLS.add(URL1);

        //Android6.0以上要申请权限
        if (Build.VERSION.SDK_INT >= 23) {
            MPermissions.requestPermissions(this, REQUECT_CODE_SDCARD, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }

        //动态注册广播接收器
        msgReceiver = new MsgReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.wyt.serviceaidldemo.RECEIVER");
        registerReceiver(msgReceiver, intentFilter);

        bindService = (Button) findViewById(R.id.bind_service);
        unbindService = (Button) findViewById(R.id.unbind_service);
        download_again = (Button) findViewById(R.id.download_again);
        bindService.setOnClickListener(this);
        download_again.setOnClickListener(this);
        unbindService.setOnClickListener(this);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.bind_service:
                startDownLoad(URLS.get(0));
                break;
            case R.id.download_again:
                startDownLoad(URLS.get(1));
                break;
            case R.id.unbind_service:
                unbindService(connection);
                break;
            default:
                break;
        }
    }

    /**
     * 广播接收器
     */
    public class MsgReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            //拿到数据，更新UI
            String info, filepath, url;//下载信息,下载路径,当前下载url
            int progress, downloadSpeed, position;//下载进度,下载速度,第几个下载链接
            url = intent.getStringExtra("url");//获取当前下载url
            position = URLS.indexOf(url);
            //判断类型
            switch (intent.getIntExtra("DownLoadState", 0)) {
                case DOWNLOADPREPARE://准备下载
                    info = intent.getStringExtra("info");
                    Log.e(TAG, "准备下载(" + (position + 1) + "):" + info);
                    break;
                case DOWNLOADING://下载中
                    downloadSpeed = intent.getIntExtra("downloadSpeed", 0);
                    progress = intent.getIntExtra("progress", 0);
                    Log.e(TAG, "下载速度(" + (position + 1) + "):" + downloadSpeed + "KB/S");
                    Log.e(TAG, "下载进度(" + (position + 1) + "):" + progress + "%");
                    break;
                case DOWNLOADSUCCESS://下载完成
                    filepath = intent.getStringExtra("info");
                    Log.e(TAG, "下载完成(" + (position + 1) + "),文件路径:" + filepath);
                    break;
                case DOWNLOADFAILE://下载失败
                    info = intent.getStringExtra("info");
                    Log.e(TAG, "下载失败(" + (position + 1) + "):" + info);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 开始下载
     * 判断服务是否已开
     * 否则开始服务后下载
     * 是则直接下载
     *
     * @param url
     */
    private void startDownLoad(String url) {
        if (myAIDLService == null) {
            message.obj = url;
            Intent bindIntent = new Intent(this, DownLoadService.class);
            bindService(bindIntent, connection, BIND_AUTO_CREATE);
        } else {
            try {
                myAIDLService.download(url);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        //停止服务
        //注销广播
        unregisterReceiver(msgReceiver);
        super.onDestroy();
    }

    /**
     * 授权回调接口
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        MPermissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    /**
     * 授权成功
     */
    @PermissionGrant(REQUECT_CODE_SDCARD)
    public void requestSdcardSuccess() {
        Toast.makeText(this, "成功获取内存权限", Toast.LENGTH_SHORT).show();
    }

    /**
     * 授权失败
     */
    @PermissionDenied(REQUECT_CODE_SDCARD)
    public void requestSdcardFailed() {
        Toast.makeText(this, "没有读取内存权限", Toast.LENGTH_SHORT).show();
    }

}
