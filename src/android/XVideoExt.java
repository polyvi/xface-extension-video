/*
 Copyright 2012-2013, Polyvi Inc. (http://polyvi.github.io/openxface)
 This program is distributed under the terms of the GNU General Public License.

 This file is part of xFace.

 xFace is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 xFace is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with xFace.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.polyvi.xface.extension.video;

import java.io.File;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;

import com.polyvi.xface.util.XConstant;
import com.polyvi.xface.util.XFileUtils;
import com.polyvi.xface.util.XLog;
import com.polyvi.xface.util.XPathResolver;
import com.polyvi.xface.util.XUtils;
import com.polyvi.xface.view.XAppWebView;

public class XVideoExt extends CordovaPlugin {
    private static final String CLASS_NAME = XVideoExt.class.getSimpleName();

    /** Video 提供给js用户的接口名字 */
    private static final String COMMAND_PLAY = "play";

    public static final int PLAY_VIDEO_REQUEST_CODE = XUtils
            .genActivityRequestCode(); // play video 的 request code
    private CallbackContext mCallbackCtx;

    private interface VideoOp {
        void run() throws Exception;
    }

    @Override
    public boolean execute(String action, final JSONArray args,
            final CallbackContext callbackContext) throws JSONException {
        if (action.equals(COMMAND_PLAY)) {
            threadhelper(new VideoOp() {
                @Override
                public void run() throws Exception {
                    mCallbackCtx = callbackContext;
                    play(args.getString(0), callbackContext);
                }
            }, callbackContext);
        } else {
            return false;
        }
        return true;
    }

    /**
     * 异步执行扩展功能，并处理结果
     *
     * @param videoOp
     * @param callbackContext
     * @param action
     */
    private void threadhelper(final VideoOp videoOp,
            final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    videoOp.run();
                } catch (Exception e) {
                    XLog.e(CLASS_NAME, e.getMessage());
                    e.printStackTrace();
                    callbackContext.error("Play video error");
                }
            }
        });
    }

    private void play(String fileUrl, CallbackContext callbackCtx) {
        XPathResolver pathResolver = new XPathResolver(fileUrl,
                ((XAppWebView) this.webView).getOwnerApp().getWorkSpace());
        CordovaResourceApi resourceApi = this.webView.getResourceApi();
        Uri videoUri = pathResolver.getUri(resourceApi);
        if (null != videoUri) {
            if (videoUri.getPath().startsWith(XConstant.ANDROID_ASSET)) {
                callbackCtx.error("Invalid video src");
                return;
            }
            if (videoUri.getScheme().equals("http")) {
                startVideoActivity(videoUri);
                return;
            }
            File file = new File(videoUri.getPath());
            if (file.exists()) {
                // 开放文件的读权限
                XFileUtils.setPermission(XFileUtils.READABLE_BY_OTHER,
                        videoUri.getPath());
                startVideoActivity(videoUri);
                return;
            }
        }
        callbackCtx.error("Video file not found error");
    }

    /**
     * 启动视频activity
     *
     * @param videoUri
     */
    private void startVideoActivity(Uri videoUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(videoUri, "video/*");
        cordova.startActivityForResult(this, intent, PLAY_VIDEO_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // 从实测来看，无论是正常播放完视频还是中途退出，都返回Activity.RESULT_CANCELED
        if (resultCode == Activity.RESULT_CANCELED) {
            if (requestCode == XVideoExt.PLAY_VIDEO_REQUEST_CODE) {
                mCallbackCtx.success("Play video end");
            }
        } else {
            mCallbackCtx.error("Play video error");
        }
    }

}
