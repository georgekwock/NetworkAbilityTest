package com.honeywell.networkabilitytest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;
import com.jcraft.jsch.SftpProgressMonitor;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	public static final String TAG = "NetWork";
	private TextView isp_text;
	private TextView monet_text;
	private TextView rssi_text;
	private TextView download_time;
	private TextView upload_time;
	private ProgressBar downbar;
	private ProgressBar upbar;
	private Button start_btn;
	private EditText ip_text;
	private EditText name_text;
	private EditText password_text;
	private String ispName;
	private String netType;
	private int rssi;
	private String downloadTime;
	private String uploadTime;
	private TelephonyManager Tel;
	private Timer timer;
	private String imei;
	private SFTPUtils ftpUtils = null;
	private String localPath;
	private TextView downTimeText;
	private TextView upTimeText;
	private String date;
	private String testInfo;
	private ChannelSftp sftp = null;
	private Session sshSession = null;
	private long startTime;
	private long endTime;
	private long runTime;
	private int avgRssi;
	private int countRssi = 0;
	private int refreshTimes = 0;
	private TextView avgRssi_text;
	private MyPhoneStateListener MyListener;
	private String serverIp;
	private String ftpUser;
	private String ftpPassword;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		isp_text = (TextView) findViewById(R.id.isp_name);
		monet_text = (TextView) findViewById(R.id.monet_name);
		rssi_text = (TextView) findViewById(R.id.rssi_name);
		avgRssi_text = (TextView) findViewById(R.id.avg_rssi_name);
		download_time = (TextView) findViewById(R.id.down_time);
		upload_time = (TextView) findViewById(R.id.up_time);
		downbar = (ProgressBar) findViewById(R.id.download_progress);
		upbar = (ProgressBar) findViewById(R.id.upload_progress);
		start_btn = (Button) findViewById(R.id.down_btn);
		downTimeText = (TextView) findViewById(R.id.downtime);
		upTimeText = (TextView) findViewById(R.id.uptime);
		ip_text = (EditText) findViewById(R.id.ip_address);
		name_text = (EditText) findViewById(R.id.ip_name);
		password_text = (EditText) findViewById(R.id.ip_psd);
		localPath = "/storage/emulated/legacy/honeywell/network/";
		timer = new Timer();
		timer.schedule(task, 100, 1000 * 3); // update every five seconds
		SimpleDateFormat sDateFormat = new SimpleDateFormat("yyyy-MM-dd  hh:mm:ss");
		date = sDateFormat.format(new java.util.Date());
		Tel = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		MyListener = new MyPhoneStateListener();
		Tel.listen(MyListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
	}

	@Override
	protected void onResume() {
		super.onResume();
		imei = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE)).getDeviceId();
		Tel.listen(MyListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
		start_btn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				serverIp = ip_text.getText().toString();
				ftpUser = name_text.getText().toString();
				ftpPassword = password_text.getText().toString();
				if (SFTPUtils.GetNetworkType(MainActivity.this).equals("No available network")) {
					Toast.makeText(MainActivity.this, "No available network, cannot test!", Toast.LENGTH_SHORT).show();
					return;
				} else if (!SFTPUtils.isFastDoubleClick()) {
					Toast.makeText(MainActivity.this, "Connecting server, please wait..", Toast.LENGTH_SHORT).show();
					DownloadTask downTask = new DownloadTask();
					downTask.execute("DownloadFile/", "NetworkAbilityTest.apk", localPath, "NetworkAbilityTest.apk"); // 下载文件
				} else if (SFTPUtils.isFastDoubleClick()) {
					Toast.makeText(MainActivity.this, "Do not repeatly click button!", Toast.LENGTH_SHORT).show();
				}

			}
		});
	}

	@Override
	protected void onPause() {
		super.onPause();
		Tel.listen(MyListener, PhoneStateListener.LISTEN_NONE);
	}

	final Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				update();
				break;
			}
			super.handleMessage(msg);
		}

		void update() {
			++refreshTimes;
			ispName = Tel.getNetworkOperatorName();
			netType = SFTPUtils.GetNetworkType(MainActivity.this);
			countRssi += rssi;
			avgRssi = countRssi / refreshTimes;
			if (ispName.length() == 0 || ispName == null) {
				isp_text.setText("No available network operators");
			} else {
				isp_text.setText(ispName);
			}
			Log.d(TAG, "rssi:" + String.valueOf(rssi));
			Log.d(TAG, "average rssi:" + String.valueOf(avgRssi));
			monet_text.setText(netType);
			rssi_text.setText(String.valueOf(rssi));
			avgRssi_text.setText(String.valueOf(avgRssi));
		}
	};
	TimerTask task = new TimerTask() {
		public void run() {
			Message message = new Message();
			message.what = 1;
			handler.sendMessage(message);
		}
	};

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}
		disconnect();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private class DownloadTask extends AsyncTask<String, Integer, String> {

		@Override
		protected void onPreExecute() {
			Log.d(TAG, "DownloadTask onPreExecute");
			super.onPreExecute();
			downbar.setProgress(0);
			downTimeText.setVisibility(View.GONE);
			start_btn.setText("Testing...");
			start_btn.setEnabled(false);
			ip_text.setEnabled(false);
			name_text.setEnabled(false);
			password_text.setEnabled(false);
		}

		@Override
		protected String doInBackground(String... params) {
			Log.d(TAG, "DownloadTask doInBackground");
			try {
				sftp = connect(ftpUser, ftpPassword, serverIp, 22);
				if (sftp == null) {
					return "error";
				} else {
					sftp.cd(params[0]);
					File file = new File(params[2] + params[3]);
					mkdirs(params[2] + params[3]);
					startTime = System.currentTimeMillis();
					sftp.get(params[1], new FileOutputStream(file), new SftpProgressMonitor() {
						private long max = 0;
						private long percent = 0;

						@Override
						public void init(int arg0, String arg1, String arg2, long max) {
							this.max = max;
						}

						@Override
						public void end() {

						}

						@Override
						public boolean count(long count) {
							percent += count;
							publishProgress((int) ((percent * 100) / max));
							return true;
						}
					});

					endTime = System.currentTimeMillis();
					runTime = endTime - startTime;
					downloadTime = String.valueOf(runTime / 1000.0);
				}

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (SftpException e) {
				e.printStackTrace();
			}
			return downloadTime;
		}

		@Override
		protected void onProgressUpdate(Integer... progress) {
			Log.d(TAG, "DownloadTask onProgressUpdate");
			super.onProgressUpdate(progress);
			downTimeText.setVisibility(View.VISIBLE);
			downTimeText.setText(String.valueOf(progress[0]) + "%");
			downbar.setProgress(progress[0]);
		}

		@Override
		protected void onPostExecute(String result) {
			Log.d(TAG, "DownloadTask onPostExecute");
			if (result == null) {
				Toast.makeText(MainActivity.this, "Download test file error!", Toast.LENGTH_SHORT).show();
				start_btn.setText("Start");
				start_btn.setEnabled(true);
				ip_text.setEnabled(true);
				name_text.setEnabled(true);
				password_text.setEnabled(true);
			} else if (result.equals("error")) {
				Toast.makeText(MainActivity.this, "Connect to server failed!", Toast.LENGTH_SHORT).show();
				start_btn.setText("Start");
				start_btn.setEnabled(true);
				ip_text.setEnabled(true);
				name_text.setEnabled(true);
				password_text.setEnabled(true);
			} else {
				downbar.setProgress(100);
				downTimeText.setVisibility(View.VISIBLE);
				downTimeText.setText("Finished");
				download_time.setText(result + "seconds");
				UploadTask uploadTask = new UploadTask();
				uploadTask.execute("UploadFile/", "test" + "_" + imei + ".apk", localPath, "NetworkAbilityTest.apk"); // 上传文件
			}

		}
	}

	private class UploadTask extends AsyncTask<String, Integer, String> {

		@Override
		protected void onPreExecute() {
			Log.d(TAG, "UploadTask onPreExecute");
			upbar.setProgress(0);
			upTimeText.setVisibility(View.GONE);
			start_btn.setText("Testing...");
			start_btn.setEnabled(false);
			ip_text.setEnabled(false);
			name_text.setEnabled(false);
			password_text.setEnabled(false);
		}

		@Override
		protected void onPostExecute(String result) {
			Log.d(TAG, "UploadTask onPostExecute");
			if (result == null) {
				Toast.makeText(MainActivity.this, "Upload test file error!", Toast.LENGTH_SHORT).show();
				start_btn.setText("Start");
				start_btn.setEnabled(true);
				ip_text.setEnabled(true);
				name_text.setEnabled(true);
				password_text.setEnabled(true);
			} else if (result.equals("error")) {
				Toast.makeText(MainActivity.this, "Connect to server failed!", Toast.LENGTH_SHORT).show();
				start_btn.setText("Start");
				start_btn.setEnabled(true);
				ip_text.setEnabled(true);
				name_text.setEnabled(true);
				password_text.setEnabled(true);
			} else {
				upTimeText.setVisibility(View.VISIBLE);
				upbar.setProgress(100);
				upTimeText.setText("Finished");
				upload_time.setText(result + "seconds");
				if (ispName.length() == 0 || ispName == null) {
					ispName = "No available network operators";
				}
				testInfo = date + " , " + ispName + " , " + SFTPUtils.GetNetworkType(MainActivity.this) + " , " + rssi
						+ " , " + avgRssi + " , " + "Download Link: " + downloadTime + " seconds " + " Upload Link: "
						+ uploadTime + " seconds ";
				SFTPUtils.WriteTxtFile(testInfo, localPath + imei + "_" + date + ".txt");
				LogTask logTask = new LogTask();
				logTask.execute("TestResult", imei + "_" + date + ".txt", localPath, imei + "_" + date + ".txt");
			}
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			super.onProgressUpdate(values);
			Log.d(TAG, "UploadTask onProgressUpdate");
			upTimeText.setVisibility(View.VISIBLE);
			upTimeText.setText(String.valueOf(values[0]) + "%");
			// 显示进度
			upbar.setProgress(values[0]);
		}

		@Override
		protected String doInBackground(String... params) {
			Log.d(TAG, "UploadTask doInBackground");
			// 这里params[0]和params[1]是execute传入的两个参数
			FileInputStream in = null;
			try {
				sftp = connect(ftpUser, ftpPassword, serverIp, 22);
				if (sftp == null) {
					return "error";
				} else {
					createDir(params[0]);
					File file = new File(params[2] + params[3]);
					in = new FileInputStream(file);
					startTime = System.currentTimeMillis();
					final long size = file.length();
					sftp.put(in, params[1], new SftpProgressMonitor() {
						private long percent = 0;

						@Override
						public void init(int arg0, String arg1, String arg2, long max) {

						}

						@Override
						public void end() {

						}

						@Override
						public boolean count(long count) {
							percent += count;
							publishProgress((int) ((percent * 100) / size));
							return true;
						}
					});
					endTime = System.currentTimeMillis();
					runTime = endTime - startTime;
					uploadTime = String.valueOf(runTime / 1000.0);
				}

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
			return uploadTime;
		}
	}

	private class LogTask extends AsyncTask<String, Integer, String> {

		@Override
		protected String doInBackground(String... params) {
			FileInputStream in = null;
			try {
				sftp = connect(ftpUser, ftpPassword, serverIp, 22);
				if (sftp == null) {
					return "error";
				} else {
					createDir(params[0]);
					File file = new File(params[2] + params[3]);
					in = new FileInputStream(file);
					startTime = System.currentTimeMillis();
					sftp.put(in, params[1]);
					endTime = System.currentTimeMillis();
					runTime = endTime - startTime;
					uploadTime = String.valueOf(runTime / 1000.0);
				}

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
			return uploadTime;
		}

		@Override
		protected void onPostExecute(String result) {
			super.onPostExecute(result);
			if (result == null) {
				Toast.makeText(MainActivity.this, "Upload log error!", Toast.LENGTH_SHORT).show();
			} else if (result.equals("error")) {
				Toast.makeText(MainActivity.this, "Connect to server failed!", Toast.LENGTH_SHORT).show();
			} else {
				Toast.makeText(MainActivity.this, "Log has been upload to server in " + result + " seconds.",
						Toast.LENGTH_SHORT).show();
			}
			start_btn.setText("Start");
			start_btn.setEnabled(true);
			ip_text.setEnabled(true);
			name_text.setEnabled(true);
			password_text.setEnabled(true);
		}

	}

	/**
	 * connect server via sftp
	 */
	public ChannelSftp connect(String username, String password, String host, int port) {
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

	private class MyPhoneStateListener extends PhoneStateListener {

		@Override
		public void onSignalStrengthsChanged(SignalStrength signalStrength) {
			if (SFTPUtils.GetNetworkType(MainActivity.this).equals("WIFI")) {
				rssi = SFTPUtils.getRssid(MainActivity.this);
			} else if (SFTPUtils.GetNetworkType(MainActivity.this).equals("2G")) {
				rssi = signalStrength.getGsmSignalStrength();
			} else if (SFTPUtils.GetNetworkType(MainActivity.this).equals("3G")) {
				rssi = signalStrength.getCdmaDbm();
			} else if (SFTPUtils.GetNetworkType(MainActivity.this).equals("4G")) {
				rssi = (2 * signalStrength.getGsmSignalStrength()) - 113;
			}

		}
	};

}
