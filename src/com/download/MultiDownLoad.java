package com.download;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public class MultiDownLoad {

    public static int perBlockSize = 5; // 每块的大小M
    public static long perBlock = perBlockSize * 1024 * 1024;
    public static volatile int threadNum = 0;
    static RandomAccessFile downLoadFile;
    public static File tmpFile;
    public static String url = "";
    public static String filePath = "";
    static long start;

    static long loadDownLoadInfo() throws Exception, IOException {
        long contentLength = 0L;
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpGet = new HttpGet(url);
            CloseableHttpResponse response = httpclient.execute(httpGet);
            try {
                HttpEntity entity = response.getEntity();
                contentLength = entity.getContentLength();

                // 发起Range请求，判断是否允许多线程下载
                HttpGet httpGet1 = new HttpGet(url);
                httpGet1.addHeader("Range", "bytes=0-" + (contentLength - 1));
                response = httpclient.execute(httpGet1);
                StatusLine statusLine = response.getStatusLine();
                int statusCode = statusLine.getStatusCode();
                if (statusCode != 206) {
                    contentLength = -1;
                }
            } finally {
                response.close();
            }
        } finally {
            httpclient.close();
        }
        return contentLength;
    }

    // 切分下载信息
    static void splitDownLoadInfoAndStartDownLoad(long contengLen) {
        long nowPosition = -1;
        for (int i = 1; ((nowPosition < contengLen)); i++) {
            threadNum = i;
            long startPosition = nowPosition + 1;
            long endPosition = startPosition + perBlock;
            nowPosition = endPosition;
            new Thread(new DownLoadThread(startPosition, endPosition)).start();
        }
    }

    // 创建临时文件
    static void createTempFile(String fileName, long length) {
        tmpFile = new File(filePath + fileName);
        try {
            downLoadFile = new RandomAccessFile(tmpFile, "rwd");
            downLoadFile.setLength(length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws Exception, Exception {
        start = System.currentTimeMillis();
        url = "http://ftp.jaist.ac.jp/pub/eclipse/oomph/epp/oxygen/R/eclipse-inst-win64.exe";
        filePath = "C:\\Users\\Administrator\\Desktop\\";
        long contengLen = loadDownLoadInfo();
        createTempFile("eclipse.exe", contengLen);
        splitDownLoadInfoAndStartDownLoad(contengLen);
        monitorDownLoadThread();
    }

    // 监控当前下载线程的信息
    private static void monitorDownLoadThread() {
        while (true) {
            try {
                TimeUnit.SECONDS.sleep(1);
                if (threadNum <= 0) {
                    long end = System.currentTimeMillis();
                    System.err.println("用时：" + ((end - start) / 1000) + "." + ((end - start) % 1000) + "S");
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

}

class DownLoadThread implements Runnable {

    private long startPosition, endPosition;

    public DownLoadThread(long startPosition, long endPosition) {
        this.startPosition = startPosition;
        this.endPosition = endPosition;
    }

    @Override
    public void run() {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        try {
            HttpGet httpGet = new HttpGet(MultiDownLoad.url);
            httpGet.addHeader("Range", "bytes=" + startPosition + "-" + (endPosition));
            System.err.println(" 下载开始位置： " + startPosition + "结束位置：" + endPosition);
            CloseableHttpResponse response = httpclient.execute(httpGet);
            try {
                HttpEntity entity = response.getEntity();
                InputStream content = entity.getContent();
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() == 206) {
                    writeTempFile2Disk(content);
                }
                MultiDownLoad.threadNum--;
            } finally {
                try {
                    response.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                httpclient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // 写入文件
    private void writeTempFile2Disk(InputStream inputStream) throws Exception {
        RandomAccessFile outputStream = new RandomAccessFile(MultiDownLoad.tmpFile, "rw");
        outputStream.seek(startPosition);
        int count = 0;
        byte[] buffer = new byte[MultiDownLoad.perBlockSize * 1024];
        while ((count = inputStream.read(buffer, 0, buffer.length)) > 0) {
            outputStream.write(buffer, 0, count);
        }
        outputStream.close();
    }

}
