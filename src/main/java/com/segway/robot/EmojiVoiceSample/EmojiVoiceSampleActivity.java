package com.segway.robot.EmojiVoiceSample;

/**
 * Created by Yi.Zhang on 2017/04/20.
 */

import android.app.Activity;
import android.content.Context;
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


public class EmojiVoiceSampleActivity extends Activity implements View.OnClickListener {
    private static final String TAG = "EmojiSampleActivity";
    private Context context;

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

    // region Handler
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ACTION_SHOW_MSG:
                    //Toast.makeText(context, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                    mTextView.setText(msg.obj.toString());
                    break;
                case ACTION_START_RECOGNITION:
                    try {
                        mRecognizer.startRecognition(mWakeupListener, mRecognitionListener);
                    } catch (VoiceException e) {
                        Log.e(TAG, "Exception: ", e);
                    }
                    break;
                case ACTION_STOP_RECOGNITION:
                    try {
                        mRecognizer.stopRecognition();
                    } catch (VoiceException e) {
                        Log.e(TAG, "Exception: ", e);
                    }
                    break;
                case ACTION_BEHAVE:
                    try {
                        mEmoji.startAnimation(RobotAnimatorFactory.getReadyRobotAnimator((Integer)msg.obj), new EmojiPlayListener() {
                            @Override
                            public void onAnimationStart(RobotAnimator animator) {
                                Log.d(TAG, "onAnimationStart: " + animator);
                            }

                            @Override
                            public void onAnimationEnd(RobotAnimator animator) {
                                Log.d(TAG, "onAnimationEnd: " + animator);
                                mEmojiView.setClickable(true);
                                mHandcontrolManager.setWorldPitch(0.6f);
                            }

                            @Override
                            public void onAnimationCancel(RobotAnimator animator) {
                                Log.d(TAG, "onAnimationCancel: " + animator);
                                mEmojiView.setClickable(true);
                                mHandcontrolManager.setWorldPitch(0.6f);
                            }
                        });
                    } catch (EmojiException e) {
                        Log.e(TAG, "onCreate: ", e);
                    }
                    break;
            }
        }
    };
    // endregion

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        context = this;

        mEmojiView = (EmojiView) findViewById(R.id.face);
        mEmojiView.setOnClickListener(this);
        mTextView = (TextView) findViewById(R.id.textView);

        initEmoji();
        initListeners();
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

        // Behave random Emoji
        Message msg = mHandler.obtainMessage(ACTION_BEHAVE, behavior);
        mHandler.sendMessage(msg);
    }

    // init listeners.
    private void initListeners() {

        mRecognitionBindStateListener = new ServiceBinder.BindStateListener() {
            @Override
            public void onBind() {
                Log.d(TAG, "Recognition onBind");
                try {
                    // make toast to indicate bind success.
                    //Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Rrecognition service bind success");
                    //mHandler.sendMessage(msg);

                    //get recognition language when service bind and init Constrained grammar.
                    mRecognitionLanguage = mRecognizer.getLanguage();
                    if (mRecognitionLanguage == Languages.EN_US)
                        initControlGrammar();
                    mRecognizer.addGrammarConstraint(mMoveSlotGrammar);

                    // if both ready, start recognition
                    mRecognitionReady = true;
                    if(mSpeakerReady && mRecognitionReady) {
                        Message msg = mHandler.obtainMessage(ACTION_START_RECOGNITION);
                        mHandler.sendMessage(msg);
                    }
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
            }

            @Override
            public void onUnbind(String s) {
                Log.d(TAG, "Recognition onUnbind");
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
                    // make toast to indicate bind success.
                    //Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Speaker service bind success");
                    //mHandler.sendMessage(msg);

                    //get speaker service language.
                    mSpeakerLanguage = mSpeaker.getLanguage();
                    if (mSpeakerLanguage != mRecognitionLanguage) {
                        Log.e(TAG, "Speakerlanguage dosen't match Recognitionlanguage!!!");
                    }

                    // Speak welcome words
                    try {
                        mSpeaker.speak("Hello, my name is Loomo.", mTtsListener);
                    } catch (VoiceException e) {
                        Log.e(TAG, "Speaker speak failed", e);
                    }

                    // if both ready, start recognition
                    mSpeakerReady = true;
                    if(mSpeakerReady && mRecognitionReady) {
                        Message msg = mHandler.obtainMessage(ACTION_START_RECOGNITION);
                        mHandler.sendMessage(msg);
                    }
                } catch (VoiceException e) {
                    Log.e(TAG, "Exception: ", e);
                }
            }

            @Override
            public void onUnbind(String s) {
                Log.d(TAG, "Speaker onUnbind");
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
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Loomo awake, you can say \"OK Loomo\" \n or touch screen");
                mHandler.sendMessage(msg);
            }

            @Override
            public void onWakeupResult(WakeupResult wakeupResult) {
                //show the wakeup result and wakeup angle.
                Log.d(TAG, "wakeup word:" + wakeupResult.getResult() + ", angle: " + wakeupResult.getAngle());
                //Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "wakeup result:" + wakeupResult.getResult() + ", angle:" + wakeupResult.getAngle());
                //mHandler.sendMessage(msg);
            }

            @Override
            public void onWakeupError(String s) {
                //show the wakeup error reason.
                Log.d(TAG, "onWakeupError:" + s);
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "wakeup error:" + s);
                mHandler.sendMessage(msg);
            }
        };

        mRecognitionListener = new RecognitionListener() {
            @Override
            public void onRecognitionStart() {
                Log.d(TAG, "onRecognitionStart");
                Message statusMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Loomo begin recognition, say:\n look up, look down, look left, look right," +
                        " turn left, turn right, turn around, turn full");
                mHandler.sendMessage(statusMsg);
            }

            @Override
            public boolean onRecognitionResult(RecognitionResult recognitionResult) {
                //show the recognition result and recognition result confidence.
                String result = recognitionResult.getRecognitionResult();
                Log.d(TAG, "recognition result: " + result +", confidence:" + recognitionResult.getConfidence());
                Message resultMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "recognition result: " + result + ", confidence:" + recognitionResult.getConfidence());
                mHandler.sendMessage(resultMsg);

                // recognize instruction
                if (result.contains("hello") || result.contains("hi")) {
                    try {
                        mRecognizer.removeGrammarConstraint(mMoveSlotGrammar);
                    } catch (VoiceException e) {
                        Log.e(TAG, "Exception: ", e);
                    }
                    //true means continuing to recognition, false means wakeup.
                    return true;
                } else if (result.contains("look") && result.contains("left")) {
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
                //show the recognition error reason.
                Log.d(TAG, "onRecognitionError: " + s);
                Message errorMsg = mHandler.obtainMessage(ACTION_SHOW_MSG, "recognition error: " + s);
                mHandler.sendMessage(errorMsg);
                return false; //to wakeup
            }
        };

        mTtsListener = new TtsListener() {
            @Override
            public void onSpeechStarted(String s) {
                //s is speech content, callback this method when speech is starting.
                Log.d(TAG, "onSpeechStarted() called with: s = [" + s + "]");
                //Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "speech start");
                //mHandler.sendMessage(msg);
            }

            @Override
            public void onSpeechFinished(String s) {
                //s is speech content, callback this method when speech is finish.
                Log.d(TAG, "onSpeechFinished() called with: s = [" + s + "]");
                //Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "speech end");
                //mHandler.sendMessage(msg);
            }

            @Override
            public void onSpeechError(String s, String s1) {
                //s is speech content, callback this method when speech occurs error.
                Log.d(TAG, "onSpeechError() called with: s = [" + s + "], s1 = [" + s1 + "]");
                Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "speech error: " + s1);
                mHandler.sendMessage(msg);
            }
        };
    }

    // bind Recognition & Speaker Services.
    private void bindServices () {
        mRecognizer = Recognizer.getInstance();
        mSpeaker = Speaker.getInstance();

        //bind the recognition service.
        mRecognizer.bindService(context, mRecognitionBindStateListener);

        //bind the speaker service.
        mSpeaker.bindService(context, mSpeakerBindStateListener);
    }

    // unbind Recognition & Speaker Services.
    private void unBindServices () {
        //unbind the recognition service and speaker service.
        mRecognizer.unbindService();
        mSpeaker.unbindService();
    }

    // init control grammar.
    private void initControlGrammar() {
        if (mRecognitionLanguage == Languages.EN_US) {
            mMoveSlotGrammar = new GrammarConstraint();
            mMoveSlotGrammar.setName("movement slots grammar");

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
        } else {
            // Recognition language dosen't support
            Log.e(TAG, "Speakerlanguage dosen't support " + mRecognitionLanguage);
            Message msg = mHandler.obtainMessage(ACTION_SHOW_MSG, "Speakerlanguage dosen't support " + mRecognitionLanguage);
            mHandler.sendMessage(msg);
        }
    }

    // init EmojiView.
    private void initEmoji() {
        mEmoji = Emoji.getInstance();
        mEmoji.init(this);
        mEmoji.setEmojiView((EmojiView) findViewById(R.id.face));
        mHandcontrolManager = new HeadControlManager(this);
        mHandcontrolManager.setMode(HeadControlHandler.MODE_FREE);
        mEmoji.setHeadControlHandler(mHandcontrolManager);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindServices();
    }
}
