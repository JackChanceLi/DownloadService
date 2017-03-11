package com.example.dowmloadservice;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.widget.Toast;

import java.io.File;

/**
 * 这个服务，是通过AsyncTask来构建的，所以不需要进行线程的管理
 * 只需要编写Binder以及AsyncTask对象运行服务就ok了
 */

public class DownloadService extends Service {


    //声明AsyncTask变量，
    private DownloadTask downloadTask;


    private String downloadUrl;//记录下载地址


    /**
     * listener是一个接口类，定义了一些方法，但是没有实现
     * 可以通过继承接口并实现方法来具有接口的方法
     * 也可以通过先完成方法的实现，来实例化一个对象的方法来获得接口中的方法
     */
    private DownloadListener listener = new DownloadListener() {
        /**
         * 根据获得的参数progress，在通知栏显示进度条
         * @param progress
         */
        @Override
        public void onProgress(int progress) {
            getNotificationManager().notify(1,getNotification("Download...",progress));
        }

        /**
         * 处理下载成功的事件
         * 下载成功，关闭通知栏进度，然后发送成功通知
         */
        @Override
        public void onSuccess() {

            downloadTask = null;
            //关闭前台服务
            stopForeground(true);
            //在通知栏创建一个通知
            getNotificationManager().notify(1,getNotification("Download Success",-1));
            //Toast一个通知
            Toast.makeText(DownloadService.this,"下载成功！",Toast.LENGTH_SHORT).show();
        }

        /**
         * 处理下载失败的回调
         * 关闭服务，通知失败
         */
        @Override
        public void onFailed() {

            downloadTask = null;

            //下载失败，前台服务关闭，
            stopForeground(true);
            //在通知栏进行通知
            getNotificationManager().notify(1,getNotification("下载失败！",-1));
            //通知栏进行通知
            Toast.makeText(DownloadService.this,"下载失败",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPaused() {
            downloadTask = null;
            //Toast通知
            Toast.makeText(DownloadService.this,"Pause",Toast.LENGTH_SHORT).show();

        }

        @Override
        public void onCanceled() {

            downloadTask = null;
            //取消操作，关闭通知栏，Toast通知
            stopForeground(true);
            Toast.makeText(DownloadService.this,"Cancle",Toast.LENGTH_SHORT).show();
        }
    };




    /****************************************************************
     *  自定义了Binder类来实现活动对服务的控制
     */

    private DownloadBinder mBinder = new DownloadBinder();

    /**
     * @param intent 参数接收来自main的intent
     * @return 将mBinder返回，被main中的onConnected接收，将binder传递到main中
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * 内部类继承Binder，内部实现对服务的控制方法
     */
    class DownloadBinder extends Binder{

        //开启下载文件
        public void startDownload(String url) {

            if (downloadTask == null) {

                downloadUrl = url;
                //声明下载Task任务
                downloadTask = new DownloadTask(listener);
                //调用execute()，开始任务
                downloadTask.execute(downloadUrl);
                //开启前台服务
                startForeground(1, getNotification("Downloading...", 0));
                Toast.makeText(DownloadService.this, "Downloading...", Toast.LENGTH_SHORT).show();
            }
        }

        public void pauseDownload() {
            if (downloadTask != null) {
                //如果后台的下载还在进行中，那么更改pause的值，控制后台服务的状态
                downloadTask.pauseDownload();
            }
        }

        public void cancelDownload() {
            if (downloadTask != null) {
                //下载正在进行，那么通过更改控制变量，取消操作
                downloadTask.cancelDownload();

            } else {//下载没有在进行


                if (downloadUrl != null) {// 取消下载时需将文件删除，并将通知关闭
                    //从Url中取出文件名字
                    String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
                    //取出存储的文件夹的名字
                    String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                    //组成路径，找到文件
                    File file = new File(directory + fileName);
                    //将文件删除
                    if (file.exists()) {
                        file.delete();
                    }
                    //停止前台服务，以及通知栏消息
                    getNotificationManager().cancel(1);
                    stopForeground(true);
                    Toast.makeText(DownloadService.this, "Canceled", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    /**
     * 获取NotificationManager
     * @return Manager
     */
    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    /**
     *
     * @param title
     * @param progress
     * @return
     */
    private Notification getNotification(String title, int progress) {

        //通过点击通知，跳转到MainActivity
        Intent intent = new Intent(this, MainActivity.class);

        //创建一个通知栏的通知
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher));
        builder.setContentIntent(pi);
        builder.setContentTitle(title);

        //在通知栏中更新下载进度
        if (progress >= 0) {
            // 当progress大于或等于0时才需显示下载进度
            builder.setContentText(progress + "%");
            builder.setProgress(100, progress, false);
        }
        return builder.build();
    }

}
