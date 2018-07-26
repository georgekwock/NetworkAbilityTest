package com.honeywell.networkabilitytest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Properties;
import java.util.Vector;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SFTPUtils {
	private String TAG = "SFTPUtils";
	private String host;
	private String username;
	private String password;
	private int port = 22;
	private ChannelSftp sftp = null;
	private Session sshSession = null;
	private long startTime;
	private long endTime;
	private long runTime;
	private static long lastClickTime = 0;
	private static long DIFF = 1000;
	private static int lastButtonId = -1;

	public SFTPUtils(String host, String username, String password) {
		this.host = host;
		this.username = username;
		this.password = password;
	}

	/**
	 * connect server via sftp
	 */
	public ChannelSftp connect() {
		JSch jsch = new JSch();
		try {
			sshSession = jsch.getSession(username, host, port);
			sshSession.setPassword(password);
			Properties sshConfig = new Properties();
			sshConfig.put("StrictHostKeyChecking", "no");
			sshSession.setConfig(sshConfig);
			sshSession.connect();
			Channel channel = sshSession.openChannel("sftp");
			if (channel != null) {
				channel.connect();
			} else {
				Log.e(TAG, "channel connecting failed.");
			}
			sftp = (ChannelSftp) channel;
		} catch (JSchException e) {
			e.printStackTrace();
		}
		return sftp;
	}

	/**
	 * 断开服务器
	 */
	public void disconnect() {
		if (this.sftp != null) {
			if (this.sftp.isConnected()) {
				this.sftp.disconnect();
				Log.d(TAG, "sftp is closed already");
			}
		}
		if (this.sshSession != null) {
			if (this.sshSession.isConnected()) {
				this.sshSession.disconnect();
				Log.d(TAG, "sshSession is closed already");
			}
		}
	}

	/**
	 * 单个文件上传
	 * 
	 * @param remotePath
	 * @param remoteFileName
	 * @param localPath
	 * @param localFileName
	 * @return
	 */
	public long uploadFile(String remotePath, String remoteFileName, String localPath, String localFileName) {
		FileInputStream in = null;
		try {
			connect();
			createDir(remotePath);
			System.out.println(remotePath);
			File file = new File(localPath + localFileName);
			in = new FileInputStream(file);
			System.out.println(in);
			startTime = System.currentTimeMillis();
			sftp.put(in, remoteFileName, new SftpProgressMonitor() {

				@Override
				public void init(int arg0, String arg1, String arg2, long arg3) {

				}

				@Override
				public void end() {

				}

				@Override
				public boolean count(long arg0) {
					return true;
				}
			});
			endTime = System.currentTimeMillis();
			runTime = endTime - startTime;
			System.out.println(sftp);
			return runTime;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SftpException e) {
			e.printStackTrace();
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return 0;
	}

	/**
	 * 单个文件下载
	 * 
	 * @param remotePath
	 * @param remoteFileName
	 * @param localPath
	 * @param localFileName
	 * @return
	 */
	public long downloadFile(String remotePath, String remoteFileName, String localPath, String localFileName) {
		try {
			connect();
			sftp.cd(remotePath);
			File file = new File(localPath + localFileName);
			mkdirs(localPath + localFileName);
			startTime = System.currentTimeMillis();
			sftp.get(remoteFileName, new FileOutputStream(file), new SftpProgressMonitor() {

				@Override
				public void init(int arg0, String arg1, String arg2, long arg3) {

				}

				@Override
				public void end() {

				}

				@Override
				public boolean count(long count) {
					Log.d("test", "count:" + count);
					return true;
				}
			});
			Log.d("test", "size:" + file.length());
			endTime = System.currentTimeMillis();
			runTime = endTime - startTime;
			return runTime;
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (SftpException e) {
			e.printStackTrace();
		}

		return 0;
	}

	/**
	 * 删除文件
	 * 
	 * @param filePath
	 * @return
	 */
	public boolean deleteFile(String filePath) {
		File file = new File(filePath);
		if (!file.exists()) {
			return false;
		}
		if (!file.isFile()) {
			return false;
		}
		return file.delete();
	}

	public boolean createDir(String createpath) {
		try {
			if (isDirExist(createpath)) {
				this.sftp.cd(createpath);
				Log.d(TAG, createpath);
				return true;
			}
			String pathArry[] = createpath.split("/");
			StringBuffer filePath = new StringBuffer("/");
			for (String path : pathArry) {
				if (path.equals("")) {
					continue;
				}
				filePath.append(path + "/");
				if (isDirExist(createpath)) {
					sftp.cd(createpath);
				} else {
					sftp.mkdir(createpath);
					sftp.cd(createpath);
				}
			}
			this.sftp.cd(createpath);
			return true;
		} catch (SftpException e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * 判断目录是否存在
	 * 
	 * @param directory
	 * @return
	 */
	@SuppressLint("DefaultLocale")
	public boolean isDirExist(String directory) {
		boolean isDirExistFlag = false;
		try {
			SftpATTRS sftpATTRS = sftp.lstat(directory);
			isDirExistFlag = true;
			return sftpATTRS.isDir();
		} catch (Exception e) {
			if (e.getMessage().toLowerCase().equals("no such file")) {
				isDirExistFlag = false;
			}
		}
		return isDirExistFlag;
	}

	public void deleteSFTP(String directory, String deleteFile) {
		try {
			sftp.cd(directory);
			sftp.rm(deleteFile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 创建目录
	 * 
	 * @param path
	 */
	public void mkdirs(String path) {
		File f = new File(path);
		String fs = f.getParent();
		f = new File(fs);
		if (!f.exists()) {
			f.mkdirs();
		}
	}

	/**
	 * 列出目录文件
	 * 
	 * @param directory
	 * @return
	 * @throws SftpException
	 */

	@SuppressWarnings("rawtypes")
	public Vector listFiles(String directory) throws SftpException {
		return sftp.ls(directory);
	}

	public static String GetNetworkType(Context context) {
		ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = cm.getActiveNetworkInfo();
		if (networkInfo != null && networkInfo.isConnected()) {
			Log.d("test", networkInfo.getTypeName());
			Log.d("test", String.valueOf(networkInfo.getType()));
			if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
				return "WIFI";
			} else if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
				TelephonyManager mTelephonyManager = (TelephonyManager) context
						.getSystemService(Context.TELEPHONY_SERVICE);
				int networkType = mTelephonyManager.getNetworkType();
				switch (networkType) {
				case TelephonyManager.NETWORK_TYPE_GPRS:
				case TelephonyManager.NETWORK_TYPE_EDGE:
				case TelephonyManager.NETWORK_TYPE_CDMA:
				case TelephonyManager.NETWORK_TYPE_1xRTT:
				case TelephonyManager.NETWORK_TYPE_IDEN:
					return "2G";
				case TelephonyManager.NETWORK_TYPE_UMTS:
				case TelephonyManager.NETWORK_TYPE_EVDO_0:
				case TelephonyManager.NETWORK_TYPE_EVDO_A:
					/**
					 * From this link https://goo.gl/R2HOjR
					 * ..NETWORK_TYPE_EVDO_0 & NETWORK_TYPE_EVDO_A EV-DO is an
					 * evolution of the CDMA2000 (IS-2000) standard that
					 * supports high data rates.
					 * 
					 * Where CDMA2000 https://goo.gl/1y10WI .CDMA2000 is a
					 * family of 3G[1] mobile technology standards for sending
					 * voice, data, and signaling data between mobile phones and
					 * cell sites.
					 */
				case TelephonyManager.NETWORK_TYPE_HSDPA:
				case TelephonyManager.NETWORK_TYPE_HSUPA:
				case TelephonyManager.NETWORK_TYPE_HSPA:
				case TelephonyManager.NETWORK_TYPE_EVDO_B:
				case TelephonyManager.NETWORK_TYPE_EHRPD:
				case TelephonyManager.NETWORK_TYPE_HSPAP:
					// Log.d("Type", "3g");
					// For 3g HSDPA , HSPAP(HSPA+) are main networktype which
					// are under 3g Network
					// But from other constants also it will 3g like HSPA,HSDPA
					// etc which are in 3g case.
					// Some cases are added after testing(real) in device with
					// 3g enable data
					// and speed also matters to decide 3g network type
					// http://goo.gl/bhtVT
					return "3G";
				case TelephonyManager.NETWORK_TYPE_LTE:
					// No specification for the 4g but from wiki
					// I found(LTE (Long-Term Evolution, commonly marketed as 4G
					// LTE))
					// https://goo.gl/9t7yrR
					return "4G";
				default:
					return "Not found";
				}
			}
		}
		return "No Available Network";
	}

	public static int getRssid(Context context) {
		WifiManager wifi_service = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		WifiInfo wifiInfo = wifi_service.getConnectionInfo();
		return wifiInfo.getRssi();
	}

	public static void WriteTxtFile(String strcontent, String strFilePath) {
		// 每次写入时，都换行写
		String strContent = strcontent + "\n";
		try {
			File file = new File(strFilePath);
			if (!file.exists()) {
				Log.d("TestFile", "Create the file:" + strFilePath);
				file.createNewFile();
			}
			RandomAccessFile raf = new RandomAccessFile(file, "rw");
			raf.seek(file.length());
			raf.write(strContent.getBytes());
			raf.close();
		} catch (Exception e) {
			Log.e("TestFile", "Error on write File.");
		}
	}

	/**
	 * 判断两次点击的间隔，如果小于1000，则认为是多次无效点击
	 *
	 * @return
	 */
	public static boolean isFastDoubleClick() {
		return isFastDoubleClick(-1, DIFF);
	}

	/**
	 * 判断两次点击的间隔，如果小于1000，则认为是多次无效点击
	 *
	 * @return
	 */
	public static boolean isFastDoubleClick(int buttonId) {
		return isFastDoubleClick(buttonId, DIFF);
	}

	/**
	 * 判断两次点击的间隔，如果小于diff，则认为是多次无效点击
	 *
	 * @param diff
	 * @return
	 */
	public static boolean isFastDoubleClick(int buttonId, long diff) {
		long time = System.currentTimeMillis();
		long timeD = time - lastClickTime;
		if (lastClickTime > 0 && timeD < diff) {
			Log.v("isFastDoubleClick", "短时间内按钮多次触发");
			return true;
		}
		lastClickTime = time;
		lastButtonId = buttonId;
		return false;
	}

}
