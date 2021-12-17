/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Shader;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcelable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Intro;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BottomPagesView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.voip.CellFlickerDrawable;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL;
import javax.microedition.khronos.opengles.GL10;

public class IntroActivity extends Activity {

    private int currentAccount = UserConfig.selectedAccount;

    private ViewPager viewPager;
    private BottomPagesView bottomPages;
    private TextView textView;
    private TextView startMessagingButton;
    private FrameLayout frameLayout2;

    private int lastPage = 0;
    private boolean justCreated = false;
    private boolean startPressed = false;
    private String[] titles;
    private String[] messages;
    private int currentViewPagerPage;
    private EGLThread eglThread;
    private long currentDate;
    private boolean justEndDragging;
    private boolean dragging;
    private int startDragX;
    private boolean destroyed;

    private LocaleController.LocaleInfo localeInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_TMessages);
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putLong("intro_crashed_time", System.currentTimeMillis()).commit();

        titles = new String[]{
                LocaleController.getString("AppName", R.string.AppName),
                LocaleController.getString("Page2Title", R.string.Page2Title),
                LocaleController.getString("Page3Title", R.string.Page3Title),
                LocaleController.getString("Page5Title", R.string.Page5Title),
                LocaleController.getString("Page4Title", R.string.Page4Title),
                LocaleController.getString("Page6Title", R.string.Page6Title)
        };
        messages = new String[]{
                LocaleController.getString("Page1Message", R.string.Page1Message),
                LocaleController.getString("Page2Message", R.string.Page2Message),
                LocaleController.getString("Page3Message", R.string.Page3Message),
                LocaleController.getString("Page5Message", R.string.Page5Message),
                LocaleController.getString("Page4Message", R.string.Page4Message),
                LocaleController.getString("Page6Message", R.string.Page6Message)
        };

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);

        FrameLayout frameLayout = new FrameLayout(this) {

            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);

                int oneFourth = (bottom - top) / 4;

                int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                frameLayout2.layout(0, y, frameLayout2.getMeasuredWidth(), y + frameLayout2.getMeasuredHeight());
                y += AndroidUtilities.dp(272);
                int x = (getMeasuredWidth() - bottomPages.getMeasuredWidth()) / 2;
                bottomPages.layout(x, y, x + bottomPages.getMeasuredWidth(), y + bottomPages.getMeasuredHeight());
                viewPager.layout(0, 0, viewPager.getMeasuredWidth(), viewPager.getMeasuredHeight());

                y = oneFourth * 3 + (oneFourth - startMessagingButton.getMeasuredHeight()) / 2;
                x = (getMeasuredWidth() - startMessagingButton.getMeasuredWidth()) / 2;
                startMessagingButton.layout(x, y, x + startMessagingButton.getMeasuredWidth(), y + startMessagingButton.getMeasuredHeight());
                y -= AndroidUtilities.dp(30);
                x = (getMeasuredWidth() - textView.getMeasuredWidth()) / 2;
                textView.layout(x, y - textView.getMeasuredHeight(), x + textView.getMeasuredWidth(), y);
            }
        };
        frameLayout.setBackgroundColor(0xffffffff);
        scrollView.addView(frameLayout, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));

        frameLayout2 = new FrameLayout(this);
        frameLayout.addView(frameLayout2, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 78, 0, 0));

        TextureView textureView = new TextureView(this);
        frameLayout2.addView(textureView, LayoutHelper.createFrame(200, 150, Gravity.CENTER));
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                if (eglThread == null && surface != null) {
                    eglThread = new EGLThread(surface);
                    eglThread.setSurfaceTextureSize(width, height);
                    eglThread.postRunnable(() -> eglThread.drawRunnable.run());
                }
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, final int width, final int height) {
                if (eglThread != null) {
                    eglThread.setSurfaceTextureSize(width, height);
                }
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                if (eglThread != null) {
                    eglThread.shutdown();
                    eglThread = null;
                }
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        viewPager = new ViewPager(this);
        viewPager.setAdapter(new IntroAdapter());
        viewPager.setPageMargin(0);
        viewPager.setOffscreenPageLimit(1);
        frameLayout.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                bottomPages.setPageOffset(position, positionOffset);

                float width = viewPager.getMeasuredWidth();
                if (width == 0) {
                    return;
                }
                float offset = (position * width + positionOffsetPixels - currentViewPagerPage * width) / width;
                Intro.setScrollOffset(offset);
            }

            @Override
            public void onPageSelected(int i) {
                currentViewPagerPage = i;
            }

            @Override
            public void onPageScrollStateChanged(int i) {
                if (i == ViewPager.SCROLL_STATE_DRAGGING) {
                    dragging = true;
                    startDragX = viewPager.getCurrentItem() * viewPager.getMeasuredWidth();
                } else if (i == ViewPager.SCROLL_STATE_IDLE || i == ViewPager.SCROLL_STATE_SETTLING) {
                    if (dragging) {
                        justEndDragging = true;
                        dragging = false;
                    }
                    if (lastPage != viewPager.getCurrentItem()) {
                        lastPage = viewPager.getCurrentItem();
                    }
                }
            }
        });

        startMessagingButton = new TextView(this) {
            CellFlickerDrawable cellFlickerDrawable;

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                if (cellFlickerDrawable == null) {
                    cellFlickerDrawable = new CellFlickerDrawable();
                    cellFlickerDrawable.drawFrame = false;
                    cellFlickerDrawable.repeatProgress = 2f;
                }
                cellFlickerDrawable.setParentWidth(getMeasuredWidth());
                AndroidUtilities.rectTmp.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                cellFlickerDrawable.draw(canvas, AndroidUtilities.rectTmp, AndroidUtilities.dp(4));
                invalidate();
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int size = MeasureSpec.getSize(widthMeasureSpec);
                if (size > AndroidUtilities.dp(260)) {
                    super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(320), MeasureSpec.EXACTLY), heightMeasureSpec);
                } else {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                }
            }
        };
        startMessagingButton.setText(LocaleController.getString("StartMessaging", R.string.StartMessaging));
        startMessagingButton.setGravity(Gravity.CENTER);
        startMessagingButton.setTextColor(0xffffffff);
        startMessagingButton.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
        startMessagingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        startMessagingButton.setBackgroundDrawable(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(4), 0xff50a8eb, 0xff439bde));
        startMessagingButton.setPadding(AndroidUtilities.dp(34), 0, AndroidUtilities.dp(34), 0);
        frameLayout.addView(startMessagingButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 42, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 36, 0, 36, 76));
        startMessagingButton.setOnClickListener(view -> {
            if (startPressed) {
                return;
            }
            startPressed = true;
            Intent intent2 = new Intent(IntroActivity.this, LaunchActivity.class);
            intent2.putExtra("fromIntro", true);
            startActivity(intent2);
            destroyed = true;
            finish();
        });

        bottomPages = new BottomPagesView(this, viewPager, 6);
        frameLayout.addView(bottomPages, LayoutHelper.createFrame(66, 5, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 350, 0, 0));

        textView = new TextView(this);
        textView.setTextColor(0xff1393d2);
        textView.setGravity(Gravity.CENTER);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 30, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));
        textView.setOnClickListener(v -> {
            if (startPressed || localeInfo == null) {
                return;
            }
            LocaleController.getInstance().applyLanguage(localeInfo, true, false, currentAccount);
            startPressed = true;
            Intent intent2 = new Intent(IntroActivity.this, LaunchActivity.class);
            intent2.putExtra("fromIntro", true);
            startActivity(intent2);
            destroyed = true;
            finish();
        });

        if (AndroidUtilities.isTablet()) {
            FrameLayout frameLayout3 = new FrameLayout(this);
            setContentView(frameLayout3);

            SizeNotifierFrameLayout backgroundTablet = new SizeNotifierFrameLayout(this) {
                @Override
                protected boolean isActionBarVisible() {
                    return false;
                }
            };
            backgroundTablet.setOccupyStatusBar(false);
            backgroundTablet.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
            frameLayout3.addView(backgroundTablet, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            FrameLayout frameLayout4 = new FrameLayout(this);
            frameLayout4.setBackgroundResource(R.drawable.btnshadow);
            frameLayout4.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
            frameLayout3.addView(frameLayout4, LayoutHelper.createFrame(498, 528, Gravity.CENTER));
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            setContentView(scrollView);
        }

        justCreated = true;

        AndroidUtilities.handleProxyIntent(this, getIntent());
        AndroidUtilities.startAppCenter(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (justCreated) {
            if (LocaleController.isRTL) {
                viewPager.setCurrentItem(6);
                lastPage = 6;
            } else {
                viewPager.setCurrentItem(0);
                lastPage = 0;
            }
            justCreated = false;
        }
        ConnectionsManager.getInstance(currentAccount).setAppPaused(false, false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConnectionsManager.getInstance(currentAccount).setAppPaused(true, false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyed = true;
        SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        preferences.edit().putLong("intro_crashed_time", 0).commit();
    }

    private class IntroAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return titles.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            TextView headerTextView = new TextView(container.getContext());
            TextView messageTextView = new TextView(container.getContext());

            FrameLayout frameLayout = new FrameLayout(container.getContext()) {
                @Override
                protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    int oneFourth = (bottom - top) / 4;
                    int y = (oneFourth * 3 - AndroidUtilities.dp(275)) / 2;
                    y += AndroidUtilities.dp(166);
                    int x = AndroidUtilities.dp(18);
                    headerTextView.layout(x, y, x + headerTextView.getMeasuredWidth(), y + headerTextView.getMeasuredHeight());

                    y += AndroidUtilities.dp(42);
                    x = AndroidUtilities.dp(16);
                    messageTextView.layout(x, y, x + messageTextView.getMeasuredWidth(), y + messageTextView.getMeasuredHeight());
                }
            };

            headerTextView.setTextColor(0xff212121);
            headerTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
            headerTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(headerTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 18, 244, 18, 0));

            messageTextView.setTextColor(0xff808080);
            messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            messageTextView.setGravity(Gravity.CENTER);
            frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.LEFT, 16, 286, 16, 0));

            container.addView(frameLayout, 0);

            headerTextView.setText(titles[position]);
            messageTextView.setText(AndroidUtilities.replaceTags(messages[position]));

            return frameLayout;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            bottomPages.setCurrentPage(position);
            currentViewPagerPage = position;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view.equals(object);
        }

        @Override
        public void restoreState(Parcelable arg0, ClassLoader arg1) {
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    public class EGLThread extends DispatchQueue {

        private final static int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
        private final static int EGL_OPENGL_ES2_BIT = 4;
        private SurfaceTexture surfaceTexture;
        private EGL10 egl10;
        private EGLDisplay eglDisplay;
        private EGLConfig eglConfig;
        private EGLContext eglContext;
        private EGLSurface eglSurface;
        private GL gl;
        private boolean initied;
        private int[] textures = new int[23];

        private long lastRenderCallTime;

        public EGLThread(SurfaceTexture surface) {
            super("EGLThread");
            surfaceTexture = surface;
        }

        private boolean initGL() {
            egl10 = (EGL10) EGLContext.getEGL();

            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglGetDisplay failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglInitialize failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            int[] configsCount = new int[1];
            EGLConfig[] configs = new EGLConfig[1];
            int[] configSpec = new int[] {
                    EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                    EGL10.EGL_RED_SIZE, 8,
                    EGL10.EGL_GREEN_SIZE, 8,
                    EGL10.EGL_BLUE_SIZE, 8,
                    EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24,
                    EGL10.EGL_STENCIL_SIZE, 0,
                    EGL10.EGL_SAMPLE_BUFFERS, 1,
                    EGL10.EGL_SAMPLES, 2,
                    EGL10.EGL_NONE
            };
            if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglChooseConfig failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            } else if (configsCount[0] > 0) {
                eglConfig = configs[0];
            } else {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglConfig not initialized");
                }
                finish();
                return false;
            }

            int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
            eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
            if (eglContext == null) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglCreateContext failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }

            if (surfaceTexture instanceof SurfaceTexture) {
                eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, surfaceTexture, null);
            } else {
                finish();
                return false;
            }

            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("createWindowSurface failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                }
                finish();
                return false;
            }
            gl = eglContext.getGL();

            GLES20.glGenTextures(23, textures, 0);
            loadTexture(R.drawable.intro_fast_arrow_shadow, 0);
            loadTexture(R.drawable.intro_fast_arrow, 1);
            loadTexture(R.drawable.intro_fast_body, 2);
            loadTexture(R.drawable.intro_fast_spiral, 3);
            loadTexture(R.drawable.intro_ic_bubble_dot, 4);
            loadTexture(R.drawable.intro_ic_bubble, 5);
            loadTexture(R.drawable.intro_ic_cam_lens, 6);
            loadTexture(R.drawable.intro_ic_cam, 7);
            loadTexture(R.drawable.intro_ic_pencil, 8);
            loadTexture(R.drawable.intro_ic_pin, 9);
            loadTexture(R.drawable.intro_ic_smile_eye, 10);
            loadTexture(R.drawable.intro_ic_smile, 11);
            loadTexture(R.drawable.intro_ic_videocam, 12);
            loadTexture(R.drawable.intro_knot_down, 13);
            loadTexture(R.drawable.intro_knot_up, 14);
            loadTexture(R.drawable.intro_powerful_infinity_white, 15);
            loadTexture(R.drawable.intro_powerful_infinity, 16);
            loadTexture(R.drawable.intro_powerful_mask, 17);
            loadTexture(R.drawable.intro_powerful_star, 18);
            loadTexture(R.drawable.intro_private_door, 19);
            loadTexture(R.drawable.intro_private_screw, 20);
            loadTexture(R.drawable.intro_tg_plane, 21);
            loadTexture(R.drawable.intro_tg_sphere, 22);

            Intro.setTelegramTextures(textures[22], textures[21]);
            Intro.setPowerfulTextures(textures[17], textures[18], textures[16], textures[15]);
            Intro.setPrivateTextures(textures[19], textures[20]);
            Intro.setFreeTextures(textures[14], textures[13]);
            Intro.setFastTextures(textures[2], textures[3], textures[1], textures[0]);
            Intro.setIcTextures(textures[4], textures[5], textures[6], textures[7], textures[8], textures[9], textures[10], textures[11], textures[12]);
            Intro.onSurfaceCreated();
            currentDate = System.currentTimeMillis() - 1000;

            return true;
        }

        public void finish() {
            if (eglSurface != null) {
                egl10.eglMakeCurrent(eglDisplay, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                eglSurface = null;
            }
            if (eglContext != null) {
                egl10.eglDestroyContext(eglDisplay, eglContext);
                eglContext = null;
            }
            if (eglDisplay != null) {
                egl10.eglTerminate(eglDisplay);
                eglDisplay = null;
            }
        }

        private Runnable drawRunnable = new Runnable() {
            @Override
            public void run() {
                if (!initied) {
                    return;
                }

                if (!eglContext.equals(egl10.eglGetCurrentContext()) || !eglSurface.equals(egl10.eglGetCurrentSurface(EGL10.EGL_DRAW))) {
                    if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.e("eglMakeCurrent failed " + GLUtils.getEGLErrorString(egl10.eglGetError()));
                        }
                        return;
                    }
                }
                float time = (System.currentTimeMillis() - currentDate) / 1000.0f;
                Intro.setPage(currentViewPagerPage);
                Intro.setDate(time);
                Intro.onDrawFrame();
                egl10.eglSwapBuffers(eglDisplay, eglSurface);

                postRunnable(() -> drawRunnable.run(), 16);
            }
        };

        private void loadTexture(int resId, int index) {
            Drawable drawable = getResources().getDrawable(resId);
            if (drawable instanceof BitmapDrawable) {
                Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
                GLES20.glBindTexture(GL10.GL_TEXTURE_2D, textures[index]);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
                GLUtils.texImage2D(GL10.GL_TEXTURE_2D, 0, bitmap, 0);
            }
        }

        public void shutdown() {
            postRunnable(() -> {
                finish();
                Looper looper = Looper.myLooper();
                if (looper != null) {
                    looper.quit();
                }
            });
        }

        public void setSurfaceTextureSize(int width, int height) {
            Intro.onSurfaceChanged(width, height, Math.min(width / 150.0f, height / 150.0f), 0);
        }

        @Override
        public void run() {
            initied = initGL();
            super.run();
        }
    }
}
