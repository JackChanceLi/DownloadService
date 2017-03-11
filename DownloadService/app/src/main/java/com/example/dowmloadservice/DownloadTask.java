package com.example.dowmloadservice;

import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.ResponseCache;
import java.util.Random;
import java.util.RandomAccess;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Created by 蚍蜉 on 2017/3/9.
 */

/**
 * AsyncTask类，
 * 1.将线程操作封装在了类里，而不用管理线程问题
 * 2.在自定义类中实现自己的任务，以及相关方法
 * 3.只要创建AsyncTask的对象，并调用.execute()，就可以实现启用
 *
 * AsyncTask有三个泛型类型参数，分别是参数类型，progress类型，以及返回值类型
 * 这里第一参数为String类型（Url地址），参数传递到doInBackground
 * 第二个参数Integer标识进度，第三个参数标识返回值，因为这里用枚举变量来标记结果
 * progress主要用于progressupdata方法，而返回值来自doInBackground
 */

public class DownloadTask extends AsyncTask<String,Integer,Integer> {


    //定义几个常量，标识下载任务的状态
    public static final int TYPE_SUCCESS = 0;//成功

    public static final int TYPE_FAILED = 1;//失败

    public static final int TYPE_PAUSED = 2;//暂停

    public static final int TYPE_CANCELED = 3;//取消


    /**
     * DownloadListener接口
     * 提供对于不同状态的处理方法
     * 在Task创建的时候，Listen作为参数传入创建
     * 在这里不实现具体方法，而是在DownloadService中实现，为了复用？
     */
    private DownloadListener listener;


    //控制变量
    private boolean isCanceled = false;

    private boolean isPaused = false;

    private int lastProgress;//记录进度


    //构造传入listener
    public DownloadTask(DownloadListener listener){
        this.listener = listener;
    }



    /**
     * onPreExecute实现的是在后台任务开启之前，进行一些UI上的初始化操作
     * 比如现实一个进度对话框
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        //可加入确认对话框等
    }



    /**
     * 在doInBackground中，是我们防止耗时代码的地方
     * 这里的代码将自动在子线程中运行
     * 注意，这里不能进行UI操作，因为UI只能运行在主线程中
     * @param params 参数为下载地址
     * @return 返回值来自doInBackground,
     */
    @Override
    protected Integer doInBackground(String... params) {//params有URL下载地址

        //耗时逻辑代码

        InputStream is = null;//java输入流（字节流，需要Byte数组来保存）

        //RandomAccessFile类是用来访问文件的
        RandomAccessFile saveFile = null;
        //File类是用来进行文件操作的
        File file = null;


        try{
            long downloadedLength = 0;//记录已经下载的文件长度

            //从参数中获取URL地址
            String downloadUrl = params[0];

            //从URL中读取下载的文件名fileName
            String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));

            //设置文件的下载目录，为SD卡的download目录
            String directory = Environment.getExternalStoragePublicDirectory
                    (Environment.DIRECTORY_DOWNLOADS).getPath();//获取Download文件夹路径


            //File类对文件进行操作
            file = new File(directory+fileName);
            if(file.exists()){//如果文件存在，那么读取已有文件的长度

                downloadedLength = file.length();//存在的话，检查已有文件长度

            }

            //从URL中获取文件的总长度
            long contentLength = getContentLength(downloadUrl);


            if(contentLength == 0){
                return TYPE_FAILED;//如果从服务器获取的长度为0，则失败
            } else if(contentLength == downloadedLength){

                //如果已经下载的和总长度相等，那么下载成功
                return TYPE_SUCCESS;
            }
            //如果已下载的小于总长度，那么开始断点下载


            //用Okhttp发起访问
            OkHttpClient client = new OkHttpClient();
            Request request = new Request.Builder()
                    //添加header说明，指定从那个字节开始下载，实现断点下载
                    .addHeader("RANGE","bytes="+downloadedLength+"-")
                    .url(downloadUrl).build();

            //发起服务器访问，并读取回复的内容
            Response response = client.newCall(request).execute();
            if(response != null){

                //将返回与java输入流连接
                is = response.body().byteStream();

                saveFile = new RandomAccessFile(file,"rw");

                saveFile.seek(downloadedLength);//跳过已经下载的字节



                /**
                 * 这里是下载文件的主要过程，从java流中提取字节流
                 * 并且在while循环中，时刻检测控制变量，进行控制
                 *
                 */
                byte[] b = new byte[1024];//inputeStream必须要通过Byte数组来接收
                int total = 0;//数据的总的字节长度
                int len;//单次的字节长度
                while ((len = is.read(b)) != -1){//将字节流写到文件一个字节数组中

                    //在循环中每次都检查控制变量，
                    if(isCanceled){
                        return TYPE_CANCELED;
                    }else if(isPaused){
                        return TYPE_PAUSED;
                    }else{

                        total += len;//计数总字节长度，为了显示进度

                        //saveFile将字节写入到本地文件
                        saveFile.write(b,0,len);

                        //计算已经下载的百分比
                        int progress = (int) ((total + downloadedLength)*100/contentLength);


                        //调用publishProgress，将peogress传递给onProgressUpdate方法
                        publishProgress(progress);
                    }
                }
                //完成下载，返回成功
                response.body().close();
                return TYPE_SUCCESS;
            }
        }catch (Exception e){
            e.printStackTrace();
        } finally {//最终处理剩余的事务
            try{
                if(is != null){
                    is.close();//关闭输入流
                }
                if(saveFile != null){
                    saveFile.close();//关闭RandomAccessFile
                }
                if(isCanceled && file != null){
                    file.delete();//如果是取消下载，删除文件
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        return TYPE_FAILED;

    }


    /**
     *界面更新的代码在这里实现
     * @param values 传入的参数来自doInBackground中调用的publishProgress
     */
    @Override
    protected void onProgressUpdate(Integer... values) {

        //参数读取progress的数值
        int progress = values[0];

        //调用listener中的方法，进行UI进度的更新
        if(progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }


    /**
     * 当任务在doInBackground中执行完毕之后，return执行结果会传递到这
     * 根据获得的结果进行一些任务后期工作，反馈任务成功信息等
     * @param status 变量是来自doInBackground的返回值
     */
    @Override
    protected void  onPostExecute(Integer status) {
        //对于不同的返回值，调用listener中的对应方法
        switch (status){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            default:
                break;
        }
    }


    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        isCanceled = true;
    }



    /**
     * 获取下载文件的总长度
     * @param downloadUrl
     * @return
     * @throws IOException
     */
    private long getContentLength(String downloadUrl)throws IOException{

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(downloadUrl).build();
        Response response = client.newCall(request).execute();


        if(request != null && response.isSuccessful()){
            long contentLengtn = response.body().contentLength();//获取内容长度
            response.close();//关闭连接
            return contentLengtn;
        }

        return 0;
    }
}
