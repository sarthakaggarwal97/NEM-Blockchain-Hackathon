/*
 * Copyright 2014 David Lázaro Esparcia.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dlazaro66.qrcodereaderview;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import org.nem.nac.qr.ScanResultStatus;

import java.io.IOException;
import java.lang.ref.WeakReference;

/**
 * QRCodeReaderView - Class which uses ZXING lib and let you easily integrate a QR decoder view.
 * Take some classes and made some modifications in the original ZXING - Barcode Scanner project.
 *
 * @author David Lázaro
 *         <p>
 *         This file has changes from original
 */
public class QRCodeReaderView extends SurfaceView
		implements SurfaceHolder.Callback, Camera.PreviewCallback {

	public interface OnQRCodeReadListener {

		void onQRCodeRead(final ScanResultStatus status, String text, PointF[] points);
	}

	private OnQRCodeReadListener mOnQRCodeReadListener;

	private static final String TAG = QRCodeReaderView.class.getName();

	private QRCodeReader  mQRCodeReader;
	private int           mPreviewWidth;
	private int           mPreviewHeight;
	private CameraManager mCameraManager;
	private boolean mQrDecodingEnabled = true;
	private DecodeFrameTask decodeFrameTask;

	public QRCodeReaderView(Context context) {
		this(context, null);
	}

	public QRCodeReaderView(Context context, AttributeSet attrs) {
		super(context, attrs);

		if (checkCameraHardware()) {
			mCameraManager = new CameraManager(getContext());
			mCameraManager.setPreviewCallback(this);

			getHolder().addCallback(this);
		}
		else {
			throw new RuntimeException("Error: Camera not found");
		}
	}

	public void setOnQRCodeReadListener(OnQRCodeReadListener onQRCodeReadListener) {
		mOnQRCodeReadListener = onQRCodeReadListener;
	}

	public boolean getQRDecodingEnabled() {
		return this.mQrDecodingEnabled;
	}

	public void setQRDecodingEnabled(boolean qrDecodingEnabled) {
		this.mQrDecodingEnabled = qrDecodingEnabled;
	}

	public void startCamera() {
		mCameraManager.startPreview();
	}

	public void stopCamera() {
		mCameraManager.stopPreview();
	}

	@Override
	public void onDetachedFromWindow() {
		super.onDetachedFromWindow();

		if (decodeFrameTask != null) {
			decodeFrameTask.cancel(true);
			decodeFrameTask = null;
		}
	}

	/****************************************************
	 * SurfaceHolder.Callback,Camera.PreviewCallback
	 ****************************************************/

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		Log.d(TAG, "surfaceCreated");

		try {
			// Indicate camera, our View dimensions
			mCameraManager.openDriver(holder, this.getWidth(), this.getHeight());
		} catch (IOException e) {
			Log.w(TAG, "Can not openDriver: " + e.getMessage());
			mCameraManager.closeDriver();
		}

		try {
			mQRCodeReader = new QRCodeReader();
			mCameraManager.startPreview();
		} catch (Exception e) {
			Log.e(TAG, "Exception: " + e.getMessage());
			mCameraManager.closeDriver();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		Log.d(TAG, "surfaceChanged");

		if (holder.getSurface() == null) {
			Log.e(TAG, "Error: preview surface does not exist");
			return;
		}

		if (mCameraManager.getPreviewSize() == null) {
			Log.e(TAG, "Error: preview size does not exist");
			return;
		}

		mPreviewWidth = mCameraManager.getPreviewSize().x;
		mPreviewHeight = mCameraManager.getPreviewSize().y;

		mCameraManager.stopPreview();

		// Fix the camera sensor rotation
		mCameraManager.setDisplayOrientation(getCameraDisplayOrientation());

		mCameraManager.setPreviewCallback(this);

		mCameraManager.startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		Log.d(TAG, "surfaceDestroyed");

		mCameraManager.setPreviewCallback(null);
		mCameraManager.stopPreview();
		mCameraManager.closeDriver();
	}

	// Called when camera take a frame
	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (!mQrDecodingEnabled || (decodeFrameTask != null
				&& decodeFrameTask.getStatus() == AsyncTask.Status.RUNNING)) {
			return;
		}

		decodeFrameTask = new DecodeFrameTask(this);
		decodeFrameTask.execute(data);
	}

	/**
	 * Check if this device has a camera
	 */
	private boolean checkCameraHardware() {
		if (getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		}
		else if (getContext().getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT)) {
			// this device has a front camera
			return true;
		}
		else if (getContext().getPackageManager()
				.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
			// this device has any camera
			return true;
		}
		else {
			// no camera on this device
			return false;
		}
	}

	/**
	 * Fix for the camera Sensor on some devices (ex.: Nexus 5x)
	 * http://developer.android.com/intl/pt-br/reference/android/hardware/Camera.html#setDisplayOrientation(int)
	 */
	@SuppressWarnings("deprecation")
	private int getCameraDisplayOrientation() {
		if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.GINGERBREAD) {
			return 90;
		}

		Camera.CameraInfo info = new Camera.CameraInfo();
		android.hardware.Camera.getCameraInfo(0, info);
		WindowManager windowManager =
				(WindowManager)getContext().getSystemService(Context.WINDOW_SERVICE);
		int rotation = windowManager.getDefaultDisplay().getRotation();
		int degrees = 0;
		switch (rotation) {
			case Surface.ROTATION_0:
				degrees = 0;
				break;
			case Surface.ROTATION_90:
				degrees = 90;
				break;
			case Surface.ROTATION_180:
				degrees = 180;
				break;
			case Surface.ROTATION_270:
				degrees = 270;
				break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360;  // compensate the mirror
		}
		else {  // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		return result;
	}

	private static class DecodeFrameTask extends AsyncTask<byte[], Void, Result> {

		private final WeakReference<QRCodeReaderView> viewRef;

		public DecodeFrameTask(QRCodeReaderView view) {
			viewRef = new WeakReference<>(view);
		}

		private volatile ScanResultStatus _scanResultStatus;

		@Override
		protected Result doInBackground(byte[]... params) {
			final QRCodeReaderView view = viewRef.get();
			if (view == null) {
				return null;
			}

			final PlanarYUVLuminanceSource source =
					view.mCameraManager.buildLuminanceSource(params[0], view.mPreviewWidth,
							view.mPreviewHeight);

			final HybridBinarizer hybBin = new HybridBinarizer(source);
			final BinaryBitmap bitmap = new BinaryBitmap(hybBin);

			try {
				final Result result = view.mQRCodeReader.decode(bitmap);
				_scanResultStatus = ScanResultStatus.OK;
				return result;
			} catch (ChecksumException e) {
				_scanResultStatus = ScanResultStatus.ERROR_BAD_CHECKSUM;
				Log.e(TAG, "ChecksumException", e);
			} catch (NotFoundException e) {
				_scanResultStatus = ScanResultStatus.ERROR_NOT_FOUND;
				Log.d(TAG, "No QR Code found");
			} catch (FormatException e) {
				_scanResultStatus = ScanResultStatus.ERROR_BAD_FORMAT;
				Log.e(TAG, "FormatException", e);
			} catch (Throwable t) {
				_scanResultStatus = ScanResultStatus.ERROR_DECODE_FAILURE;
				Log.e(TAG, "Decode failure");
			} finally {
				view.mQRCodeReader.reset();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Result result) {
			super.onPostExecute(result);

			final QRCodeReaderView view = viewRef.get();

			// Notify scan is complete
			final String text;
			final PointF[] transformedPoints;
			if (view != null && view.mOnQRCodeReadListener != null) {
				if (result != null) {
					// Transform resultPoints to View coordinates
					transformedPoints =
							transformToViewCoordinates(view, result.getResultPoints());
					text = result.getText();
				}
				else {
					transformedPoints = new PointF[0];
					text = null;
				}
				view.mOnQRCodeReadListener.onQRCodeRead(_scanResultStatus, text, transformedPoints);
			}
		}

		/**
		 * Transform result to surfaceView coordinates
		 * <p>
		 * This method is needed because coordinates are given in landscape camera coordinates.
		 * Now is working but transform operations aren't very explained
		 * <p>
		 * TODO re-write this method explaining each single value
		 *
		 * @return a new PointF array with transformed points
		 */
		private static PointF[] transformToViewCoordinates(QRCodeReaderView view,
				ResultPoint[] resultPoints) {
			PointF[] transformedPoints = new PointF[resultPoints.length];

			int index = 0;
			float previewX = view.mCameraManager.getPreviewSize().x;
			float previewY = view.mCameraManager.getPreviewSize().y;
			float scaleX = view.getWidth() / previewY;
			float scaleY = view.getHeight() / previewX;

			for (ResultPoint point : resultPoints) {
				PointF tmpPoint = new PointF((previewY - point.getY()) * scaleX, point.getX() * scaleY);
				transformedPoints[index] = tmpPoint;
				index++;
			}

			return transformedPoints;
		}
	}
}
