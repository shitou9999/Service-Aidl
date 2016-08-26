package com.wyt.serviceaidldemo;

import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.wlf.filedownloader.DownloadFileInfo;
import org.wlf.filedownloader.FileDownloadConfiguration;
import org.wlf.filedownloader.FileDownloader;
import org.wlf.filedownloader.listener.OnDeleteDownloadFileListener;
import org.wlf.filedownloader.listener.OnFileDownloadStatusListener;
import org.wlf.filedownloader.listener.simple.OnSimpleFileDownloadStatusListener;

/**
 * Created by Won on 2016/8/25.
 */
public class DownLoadService extends Service {

    public static final String TAG = "DownLoadService";
    //下载文件夹
    private final String PATH = Environment.getExternalStorageDirectory().getPath() + "/DownLoadService";
    //广播标识
    private Intent intent = new Intent("com.wyt.serviceaidldemo.RECEIVER");
    //下载标识
    private static final int DOWNLOADPREPARE = 0x00;//准备下载
    private static final int DOWNLOADING = 0x01;//下载中
    private static final int DOWNLOADSUCCESS = 0x02;//下载完成
    private static final int DOWNLOADFAILE = 0x03;//下载失败

    @Override
    public void onCreate() {
        Log.e(TAG, "onCreate() executed");
        initFileDownloader();//实例化下载模块
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e(TAG, "onStartCommand() executed");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        //销毁时取消注册,防止不必要的内存泄露麻烦
        FileDownloader.unregisterDownloadStatusListener(mOnFileDownloadStatusListener);
        Log.e(TAG, "onDestroy() executed");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * 跨进程所调用的接口
     */
    MyAIDLService.Stub mBinder = new MyAIDLService.Stub() {
        @Override
        public void download(String url) throws RemoteException {
            //这里开始下载
            FileDownloader.start(url);// 如果文件没被下载过，将创建并开启下载，否则继续下载，自动会断点续传
        }
    };

    /**
     * 实例化下载模块
     */
    private void initFileDownloader() {
        // 创建Builder
        FileDownloadConfiguration.Builder builder = new FileDownloadConfiguration.Builder(this);
        // 配置下载文件保存的文件夹
        builder.configFileDownloadDir(PATH);
        // 配置同时下载任务数量，如果不配置默认为2
        builder.configDownloadTaskSize(2);
        // 配置失败时尝试重试的次数，如果不配置默认为0不尝试
        builder.configRetryDownloadTimes(5);
        // 开启调试模式，方便查看日志等调试相关，如果不配置默认不开启
        builder.configDebugMode(true);
        // 配置连接网络超时时间，如果不配置默认为15秒
        builder.configConnectTimeout(25000);// 25秒
        // 使用配置文件初始化FileDownloader
        FileDownloadConfiguration configuration = builder.build();
        FileDownloader.init(configuration);
        //注册下载状态监听器
        FileDownloader.registerDownloadStatusListener(mOnFileDownloadStatusListener);
    }

    /**
     * 注册下载状态监听器
     */
    private OnFileDownloadStatusListener mOnFileDownloadStatusListener = new OnSimpleFileDownloadStatusListener() {
        @Override
        public void onFileDownloadStatusRetrying(DownloadFileInfo downloadFileInfo, int retryTimes) {
            // 正在重试下载（如果你配置了重试次数，当一旦下载失败时会尝试重试下载），retryTimes是当前第几次重试
        }

        @Override
        public void onFileDownloadStatusWaiting(DownloadFileInfo downloadFileInfo) {
            // 等待下载（等待其它任务执行完成，或者FileDownloader在忙别的操作）
            sendInfoBroadcast(DOWNLOADPREPARE, downloadFileInfo.getUrl(), "等待下载...");//发送广播
        }

        @Override
        public void onFileDownloadStatusPreparing(DownloadFileInfo downloadFileInfo) {
            // 准备中（即，正在连接资源）
            sendInfoBroadcast(DOWNLOADPREPARE, downloadFileInfo.getUrl(), "连接资源...");//发送广播
        }

        @Override
        public void onFileDownloadStatusPrepared(DownloadFileInfo downloadFileInfo) {
            // 已准备好（即，已经连接到了资源）
            sendInfoBroadcast(DOWNLOADPREPARE, downloadFileInfo.getUrl(), "开始下载...");//发送广播
        }

        @Override
        public void onFileDownloadStatusDownloading(DownloadFileInfo downloadFileInfo, float downloadSpeed, long
                remainingTime) {
            // 正在下载，downloadSpeed为当前下载速度，单位KB/s，remainingTime为预估的剩余时间，单位秒

            //计算下载的百分比
            float fileSize = downloadFileInfo.getFileSizeLong();
            float downloadSize = downloadFileInfo.getDownloadedSizeLong();
            int progress = (int) (downloadSize / fileSize * 100);

            //发送广播
            sendDownLoadingBroadcast(DOWNLOADING, downloadFileInfo.getUrl(), progress, (int) downloadSpeed);
        }

        @Override
        public void onFileDownloadStatusPaused(DownloadFileInfo downloadFileInfo) {
            // 下载已被暂停
            sendInfoBroadcast(DOWNLOADPREPARE, downloadFileInfo.getUrl(), "暂停下载");//发送广播
        }

        @Override
        public void onFileDownloadStatusCompleted(DownloadFileInfo downloadFileInfo) {
            // 下载完成（整个文件已经全部下载完成）

            //下载完的文件路径和名字
            String FilePath = downloadFileInfo.getFilePath();
            String FileName = downloadFileInfo.getFileName();

            sendInfoBroadcast(DOWNLOADSUCCESS, downloadFileInfo.getUrl(), FilePath);//发送广播

            FileDownloader.delete(downloadFileInfo.getUrl(), false, mOnDeleteDownloadFileListener);

        }

        @Override
        public void onFileDownloadStatusFailed(String url, DownloadFileInfo downloadFileInfo, FileDownloadStatusFailReason failReason) {
            // 下载失败了，详细查看失败原因failReason，有些失败原因你可能必须关心

            String failType = failReason.getType();
            String failUrl = failReason.getUrl();// 或：failUrl = url，url和failReason.getUrl()会是一样的

            if (FileDownloadStatusFailReason.TYPE_URL_ILLEGAL.equals(failType)) {
                // 下载failUrl时出现url错误
                sendInfoBroadcast(DOWNLOADFAILE, downloadFileInfo.getUrl(), "下载失败:url错误");//发送广播
            } else if (FileDownloadStatusFailReason.TYPE_STORAGE_SPACE_IS_FULL.equals(failType)) {
                // 下载failUrl时出现本地存储空间不足
                sendInfoBroadcast(DOWNLOADFAILE, downloadFileInfo.getUrl(), "下载失败:本地存储空间不足");//发送广播
            } else if (FileDownloadStatusFailReason.TYPE_NETWORK_DENIED.equals(failType)) {
                // 下载failUrl时出现无法访问网络
                sendInfoBroadcast(DOWNLOADFAILE, downloadFileInfo.getUrl(), "下载失败:无法访问网络");//发送广播
            } else if (FileDownloadStatusFailReason.TYPE_NETWORK_TIMEOUT.equals(failType)) {
                // 下载failUrl时出现连接超时
                sendInfoBroadcast(DOWNLOADFAILE, downloadFileInfo.getUrl(), "下载失败:连接超时");//发送广播
            } else {
                // 更多错误....
                sendInfoBroadcast(DOWNLOADFAILE, downloadFileInfo.getUrl(), "下载失败:未知错误");//发送广播
            }
            Log.e("下载失败", "" + failReason.getMessage());
        }
    };

    /**
     * 删除文件监听器
     */
    private OnDeleteDownloadFileListener mOnDeleteDownloadFileListener = new OnDeleteDownloadFileListener() {

        @Override
        public void onDeleteDownloadFilePrepared(DownloadFileInfo downloadFileNeedDelete) {

        }

        @Override
        public void onDeleteDownloadFileSuccess(DownloadFileInfo downloadFileDeleted) {

        }

        @Override
        public void onDeleteDownloadFileFailed(DownloadFileInfo downloadFileInfo, DeleteDownloadFileFailReason failReason) {

        }
    };

    /**
     * 发送广播
     *
     * @param state 下载状态
     * @param url   下载链接
     * @param info  下载信息,下载完成时info为下载路径
     */
    private void sendInfoBroadcast(int state, String url, String info) {
        intent.putExtra("DownLoadState", state);
        intent.putExtra("url", url);
        intent.putExtra("info", info);
        sendBroadcast(intent);
    }

    /**
     * 发送广播
     *
     * @param state         下载状态
     * @param url           下载链接
     * @param progress      下载进度
     * @param downloadSpeed 下载速度 KB/S
     */
    private void sendDownLoadingBroadcast(int state, String url, int progress, int downloadSpeed) {
        intent.putExtra("DownLoadState", state);
        intent.putExtra("url", url);
        intent.putExtra("progress", progress);
        intent.putExtra("downloadSpeed", downloadSpeed);
        sendBroadcast(intent);
    }

}
