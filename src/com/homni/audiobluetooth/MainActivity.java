package com.homni.audiobluetooth;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.UUID;

import com.android.webrtc.audio.MobileAEC;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AudioEffect;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

/**
 * 
 * ¼�����ţ�ֻҪ�������������������豸��������
 * 
 * @author admin
 *
 *         <p>
 *         ����: ͨ���׽��֣�Socket��ʵ������Ƶ���ݵ����紫�䣬 ������һ��ʹ��AudioRecord��ȡ��Ƶ��Ȼ��ͨ���׽��ִ����ȥ��
 *         ����һ��ͨ���׽��ֽ��պ�ʹ��AudioTrack�ಥ��
 *         <p>
 *
 *         �����¼�����ݵ�����ŵ�LinkedList��, ��LinkedList����������ﵽ2�����Ҳ�Ǿ���������֤�����������ʱ�����ݣ�ʱ��
 *         ����¼�õ����������ݴ��ͳ�ȥ��������������������޸ģ� ����ʹ˫��ͨ�������Ϻã�����ʱ�϶̣������2���ӣ���
 *         <p>
 *         �ο����ϣ�
 *         =========================
 *         1Android�е�Audio���ţ�����Audio���ͨ���л� .
 *         http://blog.csdn.net/l627859442/article/details/7918597
 *         
 *         ¼���ɼ�Դ��ַ
 *         http://blog.csdn.net/hellogv/article/details/6026455
 *         
 *         =========================
 */
public class MainActivity extends Activity
{
	private static final String TAG = "MainActivity";
	 
	private AudioManager mAudioManager;
	
	private Button btnRecord, btnExit;
	// �������ֽڴ�С
	private int recordBuffSize, playBuffSize;
	private boolean isRecording;
	/** �Ƿ��������� */
	private boolean isConnectBluetooth;

	// �ɼ���Ƶ��api��android.media.AudioRecord��
	// ��Android��¼��������MediaRecord¼���������Ƚϼ򵥡�
	// ���ǲ���רҵ�����ǲ��ܶ���Ƶ���д������Ҫ������Ƶ��ʵʱ�Ĵ��������Ƶ��һЩ��װ
	// �Ϳ�����AudioRecord������¼��
	private AudioRecord mAudioRecord;
	/** ������Ƶ����write���� */
	private AudioTrack mAudioTrack;

	// ��Ƶ��ȡԴ :��˷�
	private int audioSource = MediaRecorder.AudioSource.MIC;
	// 44.1K
	private int sampleRateInHz = 44100;
	// ������Ƶ��¼�Ƶ�����CHANNEL_IN_STEREOΪ˫������CHANNEL_CONFIGURATION_MONOΪ������
	private int channelConfig = AudioFormat./*CHANNEL_IN_MONO*/CHANNEL_IN_STEREO;
	// ��Ƶ���ݸ�ʽ:PCM 16λÿ����������֤�豸֧�֡�PCM 8λÿ����������һ���ܵõ��豸֧�֡�
	private int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

	private Receiver mReceiver;
	private IntentFilter intentFilter;

	// ������һ�����ص������������������������������ĵ���ڵ㡣
	// ����������Է������������豸����ѯ���˵��豸��ʹ����֪��MAC��ַʵ����һ�������豸�ͽ���һ��BluetoothServerSocket����Ϊ�������ˣ����������������豸�����ӡ�
	private BluetoothAdapter mAdapter = null;
	private Toast tos;

	//private BluetoothHeadset headset;
	private BluetoothDevice Device;
	private BluetoothA2dp a2dp;
	/** �����豸�������ĵ��� */
	private static final String DEVICE_NAME_CONTAIN = "Live";
	private static final boolean AECM_DEBUG = true;
	private MobileAEC aecm;
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		
		initAudio();
		bluetoothStatus();
		registerReceiver();
		/*if(isDeviceSupport())
		{
			 initAEC(audioSession);
		}
		*/
		doAECM();
		
	}


	
	/*������
	 * ������
	 * ����λ
	 * sample
	 * ����
	 * 
	 * 
	 * */
	// //////////////////////////////////////////////////////////////////////////
	// ACOUSTIC ECHO CANCELLATION MOBILE EDITION

	public void doAECM()
	{
		try
		{
			/*MobileAEC*/ aecm = new MobileAEC(null);
			aecm.setAecmMode(MobileAEC.AggressiveMode.MOST_AGGRESSIVE)
					.prepare();

			// get pcm raw data file in root of android sd card.
			// if you test this demo, you should put these files into your
			// android device or emulator.
			// the ideal output of pcm is almost down to zero.
			//���ļ��ŵ��ֻ�SD����Ŀ¼
			//��Ҫ��Ӷ�ȡSD��Ȩ��
			FileInputStream fin = new FileInputStream(new File(
					Environment.getExternalStorageDirectory()
							+ "/en-00-raw-pcm-16000Hz-16bit-mono.pcm"));

			FileOutputStream fout = new FileOutputStream(new File(
					Environment.getExternalStorageDirectory()
							+ "/aecm-output-pcm-16000Hz-16bit-mono.pcm"));

			final int cacheSize = 320;
			byte[] pcmInputCache = new byte[cacheSize];

			// core procession
			//���ļ�����������������pcmInputCache
			for (/* empty */; fin.read(pcmInputCache, 0, pcmInputCache.length) != -1; /* empty */)
			{
				// convert bytes[] to shorts[], and make it into little endian.
				short[] aecTmpIn = new short[cacheSize / 2];
				short[] aecTmpOut = new short[cacheSize / 2];
				//ͨ����װ�ķ��������Ļ����������˱���װ�����ڱ��������
				ByteBuffer.wrap(pcmInputCache)
				//����
				          .order(ByteOrder.LITTLE_ENDIAN)
				//ת�ɶ����λ�����,�ŵ�    aecTmpIn     
						  .asShortBuffer().get(aecTmpIn);
				//��������
				// aecm procession, for now the echo tail is hard-coded 10ms,
				// but you
				// should estimate it correctly each time you call
				// echoCancellation, otherwise aecm
				// cannot work.
				aecm.farendBuffer(aecTmpIn, cacheSize / 2);
				aecm.echoCancellation(aecTmpIn, null, aecTmpOut,
						(short) (cacheSize / 2), (short) 10);

				// output ��������֮���buffer
				byte[] aecBuf = new byte[cacheSize];
				ByteBuffer.wrap(aecBuf).order(ByteOrder.LITTLE_ENDIAN)
						.asShortBuffer().put(aecTmpOut);
				fout.write(aecBuf);
			}
			Log.i(TAG, "���AEC����");
			fout.close();
			fin.close();
			aecm.close();
		} catch (Exception e)
		{
			Log.e(TAG, "���� :" + e.getMessage());
			//e.printStackTrace();
		}
	}
	
	public void initView()
	{
		tos = Toast.makeText(this, "", 0);
		btnRecord = (Button) this.findViewById(R.id.btnRecord);
		btnExit = (Button) this.findViewById(R.id.btnExit);
		btnRecord.setOnClickListener(new ClickEvent());
		btnExit.setOnClickListener(new ClickEvent());
	}

	/**
	 * =========================== 
	 * ��������
	 * 
	 * ===========================
	 */

	/**
	 * �жϵ�ǰ�����Ƿ�֧��AEC����Ҫע������ļ�鲻һ��׼ȷ
	 * @return
	 */
	public boolean isDeviceSupport()
	{
		boolean flag = AcousticEchoCanceler.isAvailable();
		return flag;
	}

	//��ʼ����ʹ��AEC��
	private AcousticEchoCanceler canceler;

	public boolean initAEC(int audioSession)
	{		
		canceler = AcousticEchoCanceler.create(audioSession);
		if (canceler == null)
		{
			Log.e(TAG, "�����������ʧ�� canceler == null");
			return false;
		}
		Log.v(TAG, "������������ɹ�");
		canceler.setEnabled(true);
		return canceler.getEnabled();
	}

	//3��ʹ��/ȥʹ��AEC��

	public boolean setAECEnabled(boolean enable)
	{
		if (null == canceler)
		{
			return false;
		}
		canceler.setEnabled(enable);
		return canceler.getEnabled();
	}

	//4���ͷ�AEC��
	public boolean releaseAEC()
	{
		if (null == canceler)
		{
			return false;
		}
		canceler.setEnabled(false);
		canceler.release();
		return true;
	}

	// AcousticEchoCanceler�ĳ�ʼ����Ҫһ��sessionid������򵥵ı������ϲ�ĵ��÷�ʽ��
	private int audioSession;

	public void tesetAEC()
	{
		// 1����ʼ��AudioRecord��ʱ����Ҫ�����һ��������
		if (chkNewDev())
		{
			mAudioRecord = new AudioRecord(
					MediaRecorder.AudioSource.VOICE_COMMUNICATION,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize);
		} else
		{
			mAudioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize);
		}
		// 2����ʼ����audioRecord֮�󣬾Ϳ���ͨ��
		audioSession = mAudioRecord.getAudioSessionId();
		// ��ȡ����Ӧ��sessionid��

		// 3����ʼ��AudioTrackʱ��Ҳ��Ҫ����Ĵ���sessionid��

		if (chkNewDev() && mAudioRecord != null)
		{
			mAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize,
					AudioTrack.MODE_STREAM, audioSession);
		} else
		{
			mAudioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL,
					sampleRateInHz, channelConfig, audioFormat, recordBuffSize,
					AudioTrack.MODE_STREAM);
		}
	}

	// ���⣬����API�����ƣ���Ҫ���ǻ��Ͳ�ƥ��������

	public boolean chkNewDev()
	{
		return android.os.Build.VERSION.SDK_INT >= 16;
	}
	// Ȩ�ޣ�

	public void initAudio()
	{
	
	      
		 mAudioManager = (AudioManager)
		 getSystemService(Context.AUDIO_SERVICE);

		// ��û������ֽڴ�С
		recordBuffSize = AudioRecord.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);
		playBuffSize = AudioTrack.getMinBufferSize(sampleRateInHz,
				channelConfig, audioFormat);
 
		
		// ʵ������Ƶ�ɼ��Ͳ�����Ƶ�Ķ���
		mAudioRecord = new AudioRecord(/*MediaRecorder.AudioSource.VOICE_COMMUNICATION*/audioSource, 44100,
				AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				recordBuffSize);
		//258 260
		//audioSession = mAudioRecord.getAudioSessionId();
		mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 44100,
				AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT,
				playBuffSize, AudioTrack.MODE_STREAM/*,audioSession*/);		
		
		/*AudioEffect effect = new AudioEffect(AudioEffect.EFFECT_TYPE_ENV_REVERB, 
	              AudioEffect.EFFECT_TYPE_NULL, 
	              0, 
	              0); 
	 
		mAudioTrack.attachAuxEffect(effect.getId()); 
		mAudioTrack.setAuxEffectSendLevel(1.0f); */
	      
	}

	/**
	 * ����״̬����
	 */
	public void bluetoothStatus()
	{
		mAdapter = BluetoothAdapter.getDefaultAdapter();
		if (mAdapter == null)
		{
			Log.e(TAG, "Don't have bluetooth!");
			isConnectBluetooth = false;
		} else
		// δ����������ʾ�򿪡�
		if (!mAdapter.isEnabled())
		{
			Log.e(TAG, "[�����ر�]Please open bluetooth");
		} else
		{
			mAdapter.getProfileProxy(this, mProfileListener,
					BluetoothProfile.A2DP);
			// �Ҳ�����Ե�����
			if (Device == null)
			{
				isConnectBluetooth = false;
				Log.v(TAG, "[BluetoothService]onCreate()  [mDevice is NULL]");
			}
		}
	}

	@SuppressLint("NewApi")
	private BluetoothProfile.ServiceListener mProfileListener = new BluetoothProfile.ServiceListener()
	{
		// �����������豸����
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@SuppressLint("NewApi")
		public void onServiceConnected(int profile, BluetoothProfile proxy)
		{
			 Log.v(TAG,"[mProfileListener] onServiceConnected() ����������");

			/************************************* HEADSET *************************************/

			/**************************** A2DP ****************************/
			if (profile == BluetoothProfile.A2DP)
			{
				// LogUtil.v("[profile is A2DP]");

				a2dp = (BluetoothA2dp) proxy;
				List<BluetoothDevice> connectedDevices = proxy
						.getConnectedDevices();
				
				Log.i(TAG,"[mProfileListener] profile=A2DP ���ӵ������豸������ "+ connectedDevices.size());
				
				// �������豸ʱ
				for (BluetoothDevice device : connectedDevices)
				{
					Log.i(TAG,
							"[BluetoothService] [BluetoothProfile.ServiceListener]A2DP  �������豸�� "
									+ device.getName());
					// ======================��������======================
					Device = device;
					//break;
				}
				int connectionState = a2dp.getConnectionState(Device);
				// ����ԣ��ж��Ƿ������Party rocker ������socketͨ�š�������ʾ����ʧ��
				if (connectionState == BluetoothHeadset.STATE_CONNECTED)
				{
					Log.v(TAG,
							"[mProfileListener]  onServiceConnected() [BluetoothProfile.A2DP]������ ");
					// �豸������"Party Live"
					if (Device.getName().contains(DEVICE_NAME_CONTAIN))
					{
						isConnectBluetooth = true;
						Log.i(TAG, "ACTION_CONNECT_SUCCESS");
					} else
					{
						isConnectBluetooth = false;
						Log.i(TAG, "ACTION_CONNECT_FAIL");
					}
				}
				// û����ԣ���ʾ�������
				else if (connectionState == BluetoothA2dp.STATE_DISCONNECTED)
				{
					Log.i(TAG, "no pair bluetooth");
					isConnectBluetooth = false;
				}
			}
		}

		@Override
		public void onServiceDisconnected(int profile)
		{
			 Log.v(TAG,"[mProfileListener] onServiceDisconnected() �����Ͽ�");
		}		
	};

	/**
	 * ����Socket����
	 * 
	 * @param dev
	 */
	public void createConect(BluetoothDevice dev)
	{
		Log.i(TAG,"[createConect] Link for: " + dev.getName());
		mSocketThread = new SocketThread(dev);
		mSocketThread.start();
	}
	SocketThread mSocketThread = null;
	/** SPP UUID ͨ��Ψһʶ���� */
	private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
	private  BluetoothSocket mSocket = null;
	// �ͻ��˵����ӣ�
	// Ϊ�˳�ʼ��һ����Զ���豸�����ӣ���Ҫ�Ȼ�ȡ������豸��һ��bluetoothdevice����
	// ͨ��bluetoothdevice��������ȡbluetoothsocket����ʼ�����ӣ�
	//
	// ���岽�裺
	//
	// ʹ��bluetoothdevice������ķ���createRfcommSocketToServiceRecord(UUID)����ȡbluetoothsocket��
	// UUID����ƥ���롣
	// Ȼ�󣬵���connect������������
	// ���Զ���豸�����˸����ӣ����ǽ���ͨ�Ź����й���RFFCOMM�ŵ�������connect�����������ء�
	/*********************** ������������ **************************/
	@SuppressLint("NewApi")
	private final class SocketThread extends Thread
	{

		// private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public SocketThread(BluetoothDevice device) {
			// Use a temporary object that is later assigned to mmSocket,
			// because mmSocket is final
			BluetoothSocket tmp = null;

			mmDevice = device;

			// Get a BluetoothSocket to connect with the given BluetoothDevice
			try
				{
					// MY_UUID is the app's UUID string, also used by the server
					// code
					tmp = device.createInsecureRfcommSocketToServiceRecord(uuid);
					Log.v(TAG,"[SocketThread  Create Socket ] Success");
				} catch (IOException e)
				{

					Log.v(TAG,"[SocketThread Create Socket Error]" + e.getMessage());
				}
			// mmSocket = tmp;
			mSocket = tmp;
		}

		@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)
		@SuppressLint("NewApi")
		public void run()
		{
			// Cancel discovery because it will slow down the connection
			mAdapter.cancelDiscovery();
			

			// �ȴ�XXms�����Ͽ��������ٴ�����ʱ��Ҫʱ��׼��2014-10-07��
			try
				{
					Thread.sleep(1500);
				} catch (InterruptedException e1)
				{
					e1.printStackTrace();
				}

			try
				{
					// Connect the device through the socket. This will block
					// until it succeeds or throws an exception
				Log.v(TAG,"����Party Rocker Live�豸������...");
					mSocket.connect();// ��������
					Log.v(TAG,"[SocketThreadsocket ��connect() Success]���������豸���ӳɹ���");
					// ���ӳɹ�����������ص�UI 2014-09-18
					//mHandler.sendEmptyMessage(CONNECT_SUCCESS);
				} catch (IOException connectException)
				{
					// Unable to connect; close the socket and get out
					try
						{
						Log.e(TAG,"[socket ����ʧ��]���������豸����,ʧ�ܣ�");
						Log.e(TAG,"[mSocket.connect()] Connect Error:" + connectException.getMessage());
							// ����ʧ�ܣ��������UI�߳�
						//	mHandler.sendEmptyMessage(CONNECT_FAIL);
							mSocket.close();
							// mmSocket.close();
						} catch (IOException closeException)
						{
						}
					return;
				}

			// ȷ����ͬһʱ�̶���ÿһ����ʵ��������������Ϊ synchronized
			// �ĳ�Ա����������ֻ��һ�����ڿ�ִ��״̬����Ϊ����ֻ��һ���ܹ���ø���ʵ����Ӧ���������Ӷ���Ч���������Ա�����ķ��ʳ�ͻ��ֻҪ���п��ܷ������Ա�����ķ�����������Ϊ
			// synchronized����

			synchronized (MainActivity.this)
				{
					//connected(mSocket);
					Log.v(TAG,"[ConnectThread]" + mmDevice + " is connected.");
				}
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel()
		{
			try
				{
					mSocket.close();
					// mmSocket.close();
				} catch (IOException e)
				{
					Log.v(TAG,"[ConnectThread cancel()] " + e.getMessage());
				}
		}
	}

	
	private RecordPlayThread mRecordPlayThread;

	public synchronized void stop()
	{
		if (!isRecording)
		{
			Log.d(TAG, "Interrupting threads...");
			mRecordPlayThread.interrupt();
			mAudioRecord.stop();
			mAudioRecord.release();
			mAudioRecord = null;
		}
	}
	class ClickEvent implements View.OnClickListener
	{
		@Override
		public void onClick(View v)
		{
			if (v == btnRecord)
			{
				/*if (!isConnectBluetooth)
				{
					tos.setText("δ��������");
					tos.show();
					return;
				}*/
				isRecording = !isRecording;
				if (isRecording)
				{
					btnRecord.setText("ֹͣ");
					mRecordPlayThread = new RecordPlayThread();
					// ��һ���̱߳�¼�߷�
					mRecordPlayThread.start();
				} else
				{
					btnRecord.setText("¼��");
				}
			} else if (v == btnExit)
			{
				isRecording = false;
				MainActivity.this.finish();
			}
		}
	}

	class RecordPlayThread extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				if (AECM_DEBUG) {
					aecm = new MobileAEC(null);
					aecm.setAecmMode(MobileAEC.AggressiveMode.MILD/*MOST_AGGRESSIVE*/)
							.prepare();
				}
				
				byte[] buffer = new byte[recordBuffSize];
				// ��ʼ¼��
				mAudioRecord.startRecording();
				// ��ʼ����
				mAudioTrack.play();			
				while (isRecording /*&& isConnectBluetooth*/)
				{
					// ��MIC�������ݵ�������buffer
					int bufferReadResult = mAudioRecord.read(buffer, 0,
							recordBuffSize);

					if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION
							|| bufferReadResult == AudioRecord.ERROR_BAD_VALUE)
					{
						Log.e(TAG,"An error occured with the AudioRecord API !");
					} else
					{
						byte[] tmpBuf = new byte[bufferReadResult];

						System.arraycopy(buffer, 0, tmpBuf, 0, bufferReadResult);
						// tmpBuf = buffer.clone();

						/**
						 * ==================================
						 * 
						 * 
						 * 
						 * tmpBuf �ɼ�����Ƶ������
						 *
						 *
						 * ==================================
						 */
						
						/*---------����AEC����-----------*/
						/* final int cacheSize = tmpBuf.length;
						//byte[] pcmInputCache =tmpBuf;// new byte[tmpBuf.length];

						// core procession
						//���ļ�����������������pcmInputCache					
						// convert bytes[] to shorts[], and make it into little endian.
						short[] aecTmpIn = new short[cacheSize / 2];
						short[] aecTmpOut = new short[cacheSize / 2];
							//ͨ����װ�ķ��������Ļ����������˱���װ�����ڱ��������
							ByteBuffer.wrap(tmpBuf)
							//����
							         // .order(ByteOrder.LITTLE_ENDIAN)
							//ת�ɶ����λ�����,�ŵ�    aecTmpIn     
									  .asShortBuffer().get(aecTmpIn);
							//��������
							// aecm procession, for now the echo tail is hard-coded 10ms,
							// but you
							// should estimate it correctly each time you call
							// echoCancellation, otherwise aecm
							// cannot work.
							//TODO������...
							aecm.farendBuffer(aecTmpIn, cacheSize / 2);
							
							aecm.echoCancellation(aecTmpIn,
									null, aecTmpOut,
									(short) (cacheSize / 2), 
									//10ms
									(short) 10);

							// output ��������֮���buffer
							byte[] aecBuf = new byte[cacheSize];
							ByteBuffer.wrap(aecBuf).order(ByteOrder.LITTLE_ENDIAN)
									.asShortBuffer().put(aecTmpOut);
							
						
						Log.i(TAG, "���AEC����");			
						mAudioTrack.write(aecBuf, 0, aecBuf.length);*/	
						/*----------------------------*/
						
						// д�����ݼ�����
						mAudioTrack.write(tmpBuf, 0, tmpBuf.length);
					}
				}
				// ��ʾ
				if (!isConnectBluetooth)
				{
					Log.e(TAG, "δ���������豸");
				}
				
				// ֹͣ¼���Ͳ���
				mAudioRecord.stop();
				//mAudioRecord.release();
				//mAudioRecord = null;
				
				mAudioTrack.stop();				

				aecm.close();
			} catch (Throwable t)
			{
				Log.e(TAG, "������Ƶʧ�ܣ�"+t.getMessage());
				//tos.makeText(MainActivity.this, t.getMessage(), 1000).show();
			}
		}
	};

	/**
	 * ע��㲥
	 */
	private void registerReceiver()
	{
		mReceiver = new Receiver();
		intentFilter = new IntentFilter();
		intentFilter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		// ��������
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		// �����Ͽ�
		intentFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mReceiver, intentFilter);
	}
	
	/**
	 * ע���㲥
	 */
	private void unregisterReceiver()
	{
		unregisterReceiver(mReceiver);
	}

	private class Receiver extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			// ������������
			if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
			{
				Log.d(TAG, "�ɹ���������");
				isConnectBluetooth = true;
			} else
			// �Ͽ�����
			if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action))
			{
				isConnectBluetooth = false;
				Log.d(TAG, "û��������");
			} else 
			//ͨ���л����¼�.�������γ�
			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action))
			{
				// ����ͨ�����л�
				// Pause the playback				
				// mAudioManager.setBluetoothA2dpOn(true);
				Log.d(TAG, "ACTION_AUDIO_BECOMING_NOISY ����������ͷ����ı� "
				//true:�����豸��������
				 +"  isBluetoothA2dpOn="+mAudioManager.isBluetoothA2dpOn()
				// +"  isSpeakerphoneOn="+mAudioManager.isSpeakerphoneOn()
						);
			}
		}
	}
	
	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		isRecording = false;
		releaseAEC();
		unregisterReceiver();
		android.os.Process.killProcess(android.os.Process.myPid());
	}
}

	//��˷���ǻ�Ͳ����ν��룬ȥ������������������
	/**
	 * Acoustic Echo Cancelling
	 * 
	 * 
	 *
	 */