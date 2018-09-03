package com.cmsv6demo;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.babelstar.gviewer.NetClient;
import com.cmsv6demo.MainActivity.PlayClickListener;
import com.cmsv6demo.RecordListAdapter.PlaybackItemClick;

public class RecordActivity extends Activity {
	private ListView mLstRecord;
	private String mDevIdno;
	
	private Button mBtnStart;
	private Button mBtnStop; 
	private TextView mTvStatus; 
	
	private Handler mHandler = new Handler();
	private List<RecordFile> mFileList = new ArrayList<RecordFile>();
	private long mSearchHandle = 0;
	private SearchRunnable mSearchRunnable = new SearchRunnable();
	private RecordListAdapter mSearchAdapter;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_record);
		
		mLstRecord = (ListView) findViewById(R.id.lv_record);
		//mLstRecord.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, strs));
		// 添加点击  
		mLstRecord.setOnItemClickListener(new OnItemClickListener() {  
            @Override  
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {  
            	Intent intent = new Intent(); 
				intent.putExtra("DevIDNO", mDevIdno); 
				intent.putExtra("File", mFileList.get(arg2).getOrginalFile()); 
				intent.putExtra("Length", mFileList.get(arg2).getOrginalLen()); 
				intent.setClass(RecordActivity.this, PlaybackActivity.class);
				startActivityForResult(intent, 0);
            }  
        });  
		mSearchAdapter = new RecordListAdapter(this, new RecordFileItemClick());
		mLstRecord.setAdapter(mSearchAdapter);
		
		mBtnStart = (Button) findViewById(R.id.btnStart);
		mBtnStop = (Button) findViewById(R.id.btnStop);
		mTvStatus = (TextView) findViewById(R.id.tvStatus);
		PlayClickListener playClickListen = new PlayClickListener();
		mBtnStart.setOnClickListener(playClickListen);
		mBtnStop.setOnClickListener(playClickListen);
		
		Intent intent = getIntent();
		if (intent.hasExtra("DevIDNO")) {
			mDevIdno = intent.getStringExtra("DevIDNO");
		}
		startSearch();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		stopSearch();
		super.onDestroy();
	}
	
	protected void showSearching() {
		mTvStatus.setText("Searching");
	}

	protected void cancelSearch() {
		mTvStatus.setText("");
		stopSearch();
	}
	
	protected void startSearch() {
		if (0 == mSearchHandle) {
			showSearching();
			
			Calendar cal = Calendar.getInstance();
			mSearchHandle = NetClient.SFOpenSrchFile(mDevIdno, NetClient.GPS_FILE_LOCATION_DEVICE, NetClient.GPS_FILE_ATTRIBUTE_RECORD);
			mFileList.clear();
			//NetClient.SFStartSearchFile(mSearchHandle,2012, 12, 23, NetClient.GPS_FILE_TYPE_ALL, 0, 0, 86400);
			NetClient.SFStartSearchFile(mSearchHandle, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), NetClient.GPS_FILE_TYPE_ALL, 0, 30000, 86400);
			//NetClient.SFStartSearchFile(mSearchHandle, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), NetClient.GPS_FILE_TYPE_ALL, 0, 0, 86400);
			mHandler.postDelayed(mSearchRunnable, 200);
		}
	}
	
	protected void stopSearch() {
		if (0 != mSearchHandle) {
			NetClient.SFStopSearchFile(mSearchHandle);
			NetClient.SFCloseSearchFile(mSearchHandle);
			mHandler.removeCallbacks(mSearchRunnable);
			mSearchHandle = 0;
		}
	}
	
	final class SearchRunnable implements Runnable {
		public void run() {
			boolean isFinished = false;
			if (0 != mSearchHandle) {
				while (true) {
					byte[] result = new byte[1024];
					java.util.Arrays.fill(result, (byte)0);
					int ret = NetClient.SFGetSearchFile(mSearchHandle, result, 1024);
					if (ret == NetClient.NET_SUCCESS) {
						int i = 0;
						for (i = 0; i < result.length; ++ i) {
							if (result[i] == 0) {
								break;
							}
						}
						byte[] temp = new byte[i];
						System.arraycopy(result, 0, temp, 0, i);
						//szFileInfo:	szFile[256]:nYear:nMonth:nDay:uiBegintime:uiEndtime:szDevIDNO:nChn:nFileLen:nFileType:nLocation:nSvrID
						String fileInfo = new String(temp);
						String[] info = fileInfo.split(";");
						
						RecordFile search = new RecordFile();
						search.setOrginalFileInfo(result, i);
						
						search.setFileInfo(fileInfo);
						int index = 0;
						search.setDevIdno(mDevIdno);
						search.setName(info[index ++]);
						search.setYear(Integer.parseInt(info[index ++]));
						search.setMonth(Integer.parseInt(info[index ++]));
						search.setDay(Integer.parseInt(info[index ++]));
						search.setBeginTime(Integer.parseInt(info[index ++]));
						search.setEndTime(Integer.parseInt(info[index ++]));
						index ++;
						search.setChn(Integer.parseInt(info[index ++]));
						search.setFileLength(Integer.parseInt(info[index ++]));
						search.setFileType(Integer.parseInt(info[index ++]));
						search.setLocation(Integer.parseInt(info[index ++]));
						search.setSvrId(Integer.parseInt(info[index ++]));
						search.setIsPlaying(false);
						
						mFileList.add(search);
						continue;
					}
					else if (ret == NetClient.SEARCH_FINISHED) {
						isFinished = true;
						mSearchAdapter.setData(mFileList);
						mSearchAdapter.notifyDataSetChanged();
						
						if (mFileList.size() == 0) {
							cancelSearch();
							Toast.makeText(getApplicationContext(), "File is empty", Toast.LENGTH_SHORT).show(); 
						} else {
							cancelSearch();
						}
						break;
					} 
					else if (ret == NetClient.SEARCH_FAILED) {
						isFinished = true;
						cancelSearch();
						Toast.makeText(getApplicationContext(), "Search Finished", Toast.LENGTH_SHORT).show(); 
						break;
					}
					else 
					{
						continue;
					}
				}
			}
			
			if (!isFinished) {
				mHandler.postDelayed(mSearchRunnable, 50);
			}
		}
	}
	
	final class PlayClickListener implements OnClickListener {
		public void onClick(View v) {
			if (v.equals(mBtnStart)) {
				startSearch();
			} else if (v.equals(mBtnStop)) {
				stopSearch();
			} 
		}
	}
	
	final class RecordFileItemClick implements PlaybackItemClick {
		public void onPlaybackItemClick(int position) {
			
		}
	}
}
