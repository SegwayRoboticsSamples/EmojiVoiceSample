package com.segway.robot.EmojiVoiceSample;


import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.segway.robot.sdk.base.bind.ServiceBinder;
import com.segway.robot.sdk.emoji.Emoji;
import com.segway.robot.sdk.emoji.EmojiPlayListener;
import com.segway.robot.sdk.emoji.EmojiView;
import com.segway.robot.sdk.emoji.HeadControlHandler;
import com.segway.robot.sdk.emoji.configure.BehaviorList;
import com.segway.robot.sdk.emoji.exception.EmojiException;
import com.segway.robot.sdk.emoji.player.RobotAnimator;
import com.segway.robot.sdk.emoji.player.RobotAnimatorFactory;
import com.segway.robot.sdk.voice.Languages;
import com.segway.robot.sdk.voice.Recognizer;
import com.segway.robot.sdk.voice.Speaker;
import com.segway.robot.sdk.voice.VoiceException;
import com.segway.robot.sdk.voice.grammar.GrammarConstraint;
import com.segway.robot.sdk.voice.grammar.Slot;
import com.segway.robot.sdk.voice.recognition.RecognitionListener;
import com.segway.robot.sdk.voice.recognition.RecognitionResult;
import com.segway.robot.sdk.voice.recognition.WakeupListener;
import com.segway.robot.sdk.voice.recognition.WakeupResult;
import com.segway.robot.sdk.voice.tts.TtsListener;


/**
 * @author jacob
 */
public class EmojiVoiceSampleActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "Loomo";

    private static final int ACTION_SHOW_MSG = 1;
    private static final int ACTION_START_RECOGNITION = 2;
    private static final int ACTION_STOP_RECOGNITION = 3;
    private static final int ACTION_BEHAVE = 4;

    private TextView mTextView;
    private EmojiView mEmojiView;
    private Emoji mEmoji;
    private Recognizer mRecognizer;
    private Speaker mSpeaker;
    private int mSpeakerLanguage;
    private int mRecognitionLanguage;
    private GrammarConstraint mMoveSlotGrammar;
    private boolean mRecognitionReady;
    private boolean mSpeakerReady;
    private HeadControlManager mHandcontrolManager;

    private ServiceBinder.BindStateListener mRecognitionBindStateListener;
    private ServiceBinder.BindStateListener mSpeakerBindStateListener;
    private WakeupListener mWakeupListener;
    private RecognitionListener mRecognitionListener;
    private TtsListener mTtsListener;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ACTION_SHOW_MSG:
                    mTextView.setText(msg.obj.toString());
                    break;
                case ACTION_START_RECOGNITION:
                    try {
                        mRecognizer.startWakeupAndRecognition(mWakeupListener, mRecognitionListener);
                    } catch (VoiceException e) {
                        e.printStackTrace();
                    }
                    break;
                case ACTION_STOP_RECOGNITION:
                    try {
                        mRecognizer.stopRecognition();
                    } catch (VoiceException e) {
                        e.printStackTrace();
                    }
                    break;
                case ACTION_BEHAVE:
                    try {
                        mEmoji.startAnimation(RobotAnimatorFactory.getReadyRobotAnimator((Integer) msg.obj), new EmojiPlayListener() {
                            @Override
                            public void onAnimationStart(RobotAnimator animator) {
                            }

                            @Override
                            public void onAnimationEnd(RobotAnimator animator) {
                                mEmojiView.setClickable(true);
                                mHandcontrolManager.setWorldPitch(0.6f);
                            }

                            @Override
                            public void onAnimationCancel(RobotAnimator animator) {
                                mEmojiView.setClickable(true);
                                mHandcontrolManager.setWorldPitch(0.6f);
                            }
                        });
                    } catch (EmojiException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mEmojiView = (EmojiView) findViewById(R.id.face);
        mEmojiView.setOnClickListener(this);
        mTextView = (TextView) findViewById(R.id.textView);

        initEmoji();
        initListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindServices();
    }

    @Override
    public void onClick(final View v) {
        v.setClickable(false);
        int behavior;
        int randomSeed = (int) (Math.random() * 4);
        switch (randomSeed) {
            case 0:
                behavior = BehaviorList.LOOK_LEFT;
                break;
            case 1:
                behavior = BehaviorList.LOOK_RIGHT;
                break;
            case 2:
                behavior = BehaviorList.LOOK_AROUND;
                break;
            case 3:
                behavior = BehaviorList.LOOK_CURIOUS;
                break;
            default:
                behavior = BehaviorList.LOOK_AROUND;
                break;
        }
        Message msg = mHandler.obtainMessage(ACTION_BEHAVE, behavior);
        mHandler.sendMessage(msg);
    }

    private void initListeners() {

        mRecognitionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "Recognition onBind");
                try {
                    //get recognition language when service bind and init Constrained grammar.
                    mRecognitionLanguage = mRecognizer.getLanguage();
                    if (mRecognitionLanguage == Languages.EN_US) {
                        initControlGrammar();
                        mRecognizer.addGrammarConstraint(mMoveSlotGrammar);
                        // if both ready, start recognition
                        mRecognitionReady = true;
                        if (mSpeakerReady && mRecognitionReady) {
                            Message msg = mHandler.obtainMessage(ACTION_START_RECOGNITION);
                            mHandler.sendMessage(msg);
                        }
                    } else {
                        mEmojiView.setClickable(false);
                        Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Only US English is supported");
                        mHandler.sendMessage(msg);
                    }

                } catch (VoiceException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnbind(String s) {
                // make toast to indicate unbind success.
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Recognition service disconnected");
                mHandler.sendMessage(msg);

                // Stop recognition
                mRecognitionReady = false;
                msg = mHandler.obtainMessage(ACTION_STOP_RECOGNITION);
                mHandler.sendMessage(msg);
            }
        };

        mSpeakerBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "Speaker onBind");
                try {

                    mSpeakerLanguage = mSpeaker.getLanguage();
                    if (mSpeakerLanguage == Languages.EN_US) {
                        try {
                            mSpeaker.speak("Hello, my name is Loomo.", mTtsListener);
                        } catch (VoiceException e) {
                            e.printStackTrace();
                        }

                        // if both ready, start recognition
                        mSpeakerReady = true;
                        if (mSpeakerReady && mRecognitionReady) {
                            Message msg = mHandler.obtainMessage(ACTION_START_RECOGNITION);
                            mHandler.sendMessage(msg);
                        }
                    }else {
                        mEmojiView.setClickable(false);
                        Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Only US English is supported");
                        mHandler.sendMessage(msg);
                    }
                } catch (VoiceException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onUnbind(String s) {
                // make toast to indicate unbind success.
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Speaker service unbind success");
                mHandler.sendMessage(msg);

                // stop recognition
                mSpeakerReady = false;
                msg = mHandler.obtainMessage(ACTION_STOP_RECOGNITION);
                mHandler.sendMessage(msg);
            }
        };

        mWakeupListener = new WakeupListener() {
            @Override
            public void onStandby() {
                Log.d(TAG, "onStandby");
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "You can say \"Ok Loomo\" \n or touch the screen to wake up Loomo");
                mHandler.sendMessage(msg);
            }

            @Override
            public void onWakeupResult(WakeupResult wakeupResult) {
                Log.d(TAG, "wakeup word:" + wakeupResult.getResult() + ", angle: " + wakeupResult.getAngle());
            }

            @Override
            public void onWakeupError(String s) {
                Log.d(TAG, "onWakeupError:" + s);
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "wakeup error:" + s);
                mHandler.sendMessage(msg);
            }
        };

        mRecognitionListener = new RecognitionListener() {
            @Override
            public void onRecognitionStart() {
                Log.d(TAG, "onRecognitionStart");
                Message statusMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Loomo begin to recognize, say:\n look up, look down, look left, look right," +
                        " turn left, turn right, turn around, turn full");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public boolean onRecognitionResult(RecognitionResult recognitionResult) {
                //show the recognition result and recognition result confidence.
                String result = recognitionResult.getRecognitionResult();
                Log.e(TAG, "recognition result: " + result + ", confidence:" + recognitionResult.getConfidence());
                Message resultMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "recognition result: " + result + ", confidence:" + recognitionResult.getConfidence());
                mHandler.sendMessage(resultMsg);

               if (result.contains("look") && result.contains("left")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.LOOK_LEFT);
                    mHandler.sendMessage(msg);
                } else if (result.contains("look") && result.contains("right")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.LOOK_RIGHT);
                    mHandler.sendMessage(msg);
                } else if (result.contains("look") && result.contains("up")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.LOOK_UP);
                    mHandler.sendMessage(msg);
                } else if (result.contains("look") && result.contains("down")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.LOOK_DOWN);
                    mHandler.sendMessage(msg);
                } else if (result.contains("turn") && result.contains("left")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.TURN_LEFT);
                    mHandler.sendMessage(msg);
                } else if (result.contains("turn") && result.contains("right")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.TURN_RIGHT);
                    mHandler.sendMessage(msg);
                } else if (result.contains("turn") && result.contains("around")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.TURN_AROUND);
                    mHandler.sendMessage(msg);
                } else if (result.contains("turn") && result.contains("full")) {
                    Message msg = mHandler.obtainMessage(ACTION_BEHAVE, BehaviorList.TURN_FULL);
                    mHandler.sendMessage(msg);
                }
                return false;
            }

            @Override
            public boolean onRecognitionError(String s) {
                Log.d(TAG, "onRecognitionError: " + s);
                Message errorMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "recognition error: " + s);
                mHandler.sendMessage(errorMsg);
                return false;
            }
        };

        mTtsListener = new TtsListener() {
            @Override
            public void onSpeechStarted(String s) {
                Log.d(TAG, "onSpeechStarted() called with: s = [" + s + "]");
            }

            @Override
            public void onSpeechFinished(String s) {
                Log.d(TAG, "onSpeechFinished() called with: s = [" + s + "]");
            }

            @Override
            public void onSpeechError(String s, String s1) {
                Log.d(TAG, "onSpeechError() called with: s = [" + s + "], s1 = [" + s1 + "]");
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "speech error: " + s1);
                mHandler.sendMessage(msg);
            }
        };
    }

    private void bindServices() {
        mRecognizer = Recognizer.getInstance();
        mSpeaker = Speaker.getInstance();
        mRecognizer.bindService(this, mRecognitionBindStateListener);
        mSpeaker.bindService(this, mSpeakerBindStateListener);
    }

    private void unBindServices() {
        mRecognizer.unbindService();
        mSpeaker.unbindService();
    }

    private void initControlGrammar() {
        mMoveSlotGrammar = new GrammarConstraint();
        mMoveSlotGrammar.setName("movement orders");

        Slot moveSlot = new Slot("movement");
        moveSlot.setOptional(false);
        moveSlot.addWord("look");
        moveSlot.addWord("turn");
        mMoveSlotGrammar.addSlot(moveSlot);

        Slot orientationSlot = new Slot("orientation");
        orientationSlot.setOptional(false);
        orientationSlot.addWord("right");
        orientationSlot.addWord("left");
        orientationSlot.addWord("up");
        orientationSlot.addWord("down");
        orientationSlot.addWord("full");
        orientationSlot.addWord("around");
        mMoveSlotGrammar.addSlot(orientationSlot);
    }

    private void initEmoji() {
        mEmoji = Emoji.getInstance();
        mEmoji.init(this);
        mEmoji.setEmojiView((EmojiView) findViewById(R.id.face));
        mHandcontrolManager = new HeadControlManager(this);
        mHandcontrolManager.setMode(HeadControlHandler.MODE_FREE);
        mEmoji.setHeadControlHandler(mHandcontrolManager);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unBindServices();
        finish();
    }
}
