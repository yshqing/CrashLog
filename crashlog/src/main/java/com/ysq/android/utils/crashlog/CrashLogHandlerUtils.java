package com.ysq.android.utils.crashlog;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class CrashLogHandlerUtils implements UncaughtExceptionHandler {

	private static CrashLogHandlerUtils mSelf;

	// 系统默认的UncaughtException处理类
	private UncaughtExceptionHandler mDefaultHandler;
	// 程序的Context对象
	private Context mContext;
	/**
	 * 保存log的文件夹
	 */
	private File mSaveDir;

	/**
	 * 在log上面添加的额外信息
	 */
	private String mTopMsg;
	/**
	 * 在log下面添加的额外信息
	 */
	private String mBottomMsg;
	/**
	 * 在保存新log的同时是否删除7天前的log
	 */
	private boolean mDeleteOld;
	// 用于格式化日期,作为日志文件名的一部分
	private DateFormat mFormatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

	private CrashLogHandlerUtils(Application application) {
		mContext = application;
		// 设置log默认保存目录
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			mSaveDir = application.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		}
		// 设置默认的在log上面添加的额外信息
		setDefaultsTopMsg();
		// 设置默认的在log下面添加的额外信息
		setDefaultsBottomMsg();
	}

	/**
	 * @param application 运行环境
	 * @return 返回实例
	 */
	public static synchronized CrashLogHandlerUtils getInstance(Application application) {
		if (mSelf == null) {
			mSelf = new CrashLogHandlerUtils(application);
		}
		return mSelf;
	}

	/**
	 * 设置保存log的文件夹,默认为{@link Environment#DIRECTORY_DOCUMENTS}
	 * @param saveDir 保存log的文件夹
	 * @return self
	 */
	public CrashLogHandlerUtils setSavePath(File saveDir) {
		mSelf.mSaveDir = saveDir;
		return mSelf;
	}

	/**
	 * 设置在log上面添加的额外信息
	 * @param topMsg 添加在log上面添加的额外信息
	 * @return self
	 */
	public CrashLogHandlerUtils setLogTopMessage(String topMsg) {
		mSelf.mTopMsg = topMsg;
		return mSelf;
	}

	/**
	 * 设置在log下面添加的额外信息
	 * @param bottomMsg 添加在log下面添加的额外信息
	 * @return self
	 */
	public CrashLogHandlerUtils setLogBottomMessage(String bottomMsg) {
		mSelf.mBottomMsg = bottomMsg;
		return mSelf;
	}

	/**
	 * 设置在保存新log的同时是否删除7天前的log, 默认为{@code false}
	 * @param deleteOldLog 是否删除
	 * @return self
	 */
	public CrashLogHandlerUtils setDeleteOldLog(boolean deleteOldLog) {
		mSelf.mDeleteOld = deleteOldLog;
		return mSelf;
	}

	/**
	 * 启动异常退出log捕捉
	 */
	public void start() throws Exception {
		if (mSaveDir == null) {
			throw new Exception("设置的log保存路径不存在");
		}
		// 获取系统默认的UncaughtException处理器
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		// 设置该CrashHandler为程序的默认处理器
		Thread.setDefaultUncaughtExceptionHandler(this);
	}



	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		if (!handleException(ex) && mDefaultHandler != null) {
			mDefaultHandler.uncaughtException(thread, ex);
		}
	}

	/**
	 * @param ex 异常
	 * @return @code false,因为本工具只保存异常日志,不捕获异常,所以只返回@code false
	 */
	private boolean handleException(Throwable ex) {
		if (ex != null) {
			saveCrashInfo2File(ex);
		}
		return false;
	}

	/**
	 * 保存错误信息到文件中
	 * 
	 * @param ex Crash异常
	 * @return 是否保存成功
	 */
	private boolean saveCrashInfo2File(Throwable ex) {
		Calendar calendar = Calendar.getInstance();
		
		StringBuffer sb = new StringBuffer();
		sb.append(mTopMsg);
		
		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		ex.printStackTrace(printWriter);
		Throwable cause = ex.getCause();
		while (cause != null) {
			cause.printStackTrace(printWriter);
			cause = cause.getCause();
		}
		printWriter.close();
		String result = writer.toString();
		sb.append(result);
		
		sb.append(mBottomMsg);
		
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			File dir = mSaveDir;
			if (!dir.exists()) {
				dir.mkdirs();
			}
			FileWriter fWriter = null;
			try {
				fWriter = new FileWriter(mSaveDir + "/" + getFileNameByCalendar(calendar), true);
				fWriter.write(sb.toString());
				fWriter.flush();
				fWriter.close();
				if (mDeleteOld) {
					deleteOldFile(calendar);
				}
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				if (fWriter != null) {
					try {
						fWriter.flush();
						fWriter.close();
					} catch (IOException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
		return false;
	}

	/**
	 * 设置默认的在log上面添加的额外信息
	 */
	private void setDefaultsTopMsg() {
		StringBuffer sb = new StringBuffer();
		Calendar calendar = Calendar.getInstance();
		Date date = new Date(calendar.getTimeInMillis());
		long timestamp = calendar.getTimeInMillis();
		String time = mFormatter.format(date);
		sb.append("--------本次错误开始--------\n");
		sb.append(time).append("_").append(timestamp).append("\n");
		try {
			PackageManager pm = mContext.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
			if (pi != null) {
				String versionName = pi.versionName == null ? "null" : pi.versionName;
				String versionCode = pi.versionCode + "";
				sb.append("versionName:").append(versionName).append("\n");
				sb.append("versionCode:").append(versionCode).append("\n");
			}
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
		mTopMsg = sb.toString();
	}

	/**
	 * 设置默认的在log下面添加的额外信息
	 */
	private void setDefaultsBottomMsg() {
		StringBuffer sb = new StringBuffer();
		sb.append("--------本次错误结束---------").append("\n\n");
		mBottomMsg = sb.toString();
	}

	/**
	 * 删除给定日期的七天(含)前的及以后的log
	 * @param todayCalendar 所以日期
	 */
	private void deleteOldFile(Calendar todayCalendar) {
		File[] files = mSaveDir.listFiles();
		if (files != null && files.length > 0) {
			for (File file : files) {
				Calendar oldCalendar = (Calendar) todayCalendar.clone();
				oldCalendar.set(Calendar.DAY_OF_MONTH, oldCalendar.get(Calendar.DAY_OF_MONTH) - 1);
				if (file.isFile() && file.exists() && !file.getName().equals(getFileNameByCalendar(todayCalendar))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -1)))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -2)))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -3)))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -4)))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -5)))
						&& !file.getName().equals(getFileNameByCalendar(getCalendarByCorrection(todayCalendar, -6)))) {
					try {
						file.delete();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	/**
	 * 获取所给日期的修正后的日期
	 * @param calendar 原始日期
	 * @param correctionDay 修正值,单位为天
	 * @return 修正后的日期
	 */
	private Calendar getCalendarByCorrection(Calendar calendar, int correctionDay) {
		Calendar oldCalendar = (Calendar) calendar.clone();
		oldCalendar.set(Calendar.DAY_OF_MONTH, oldCalendar.get(Calendar.DAY_OF_MONTH) + correctionDay);
		return oldCalendar;
	}

	/**
	 * 根据日期获取文件名
	 * @param calendar 日期
	 * @return 文件名
	 */
	private String getFileNameByCalendar(Calendar calendar) {
		if (calendar == null) {
			return null;
		}
		Date date = new Date(calendar.getTimeInMillis());
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
		String fileName = "crash_log" + "_" + formatter.format(date) + ".txt";
		return fileName;
	}
}
