package com.example.dowmloadservice;

/**
 * Created by 蚍蜉 on 2017/3/9.
 * 这是一个接口，内部都是共有的方法
 *  可以被任何类继承，或者，在别的类中实例化对象（先实现方法）
 */

//定义回调接口，用于对下载过程各种状态进行监听和回调
public interface DownloadListener {

    //用于通知当前的下载进度
    void onProgress(int progress);

    //用于通知下载成功事件
    void onSuccess();

    //用于通知下载失败事件
    void onFailed();

    //用于通知下载暂停事件
    void onPaused();

    //用于通知下载取消事件
    void onCanceled();
}
