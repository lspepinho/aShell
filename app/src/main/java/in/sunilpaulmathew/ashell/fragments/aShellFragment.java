package in.sunilpaulmathew.ashell.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textview.MaterialTextView;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import in.sunilpaulmathew.ashell.BuildConfig;
import in.sunilpaulmathew.ashell.R;
import in.sunilpaulmathew.ashell.activities.ExamplesActivity;
import in.sunilpaulmathew.ashell.activities.aShellActivity;
import in.sunilpaulmathew.ashell.adapters.CommandsAdapter;
import in.sunilpaulmathew.ashell.adapters.ShellOutputAdapter;
import in.sunilpaulmathew.ashell.utils.Commands;
import in.sunilpaulmathew.ashell.utils.ShizukuShell;
import in.sunilpaulmathew.ashell.utils.Utils;
import rikka.shizuku.Shizuku;

/*
 * Created by sunilpaulmathew <sunil.kde@gmail.com> on October 28, 2022
 */
public class aShellFragment extends Fragment {

    private AppCompatAutoCompleteTextView mCommand;
    private AppCompatImageButton mBottomArrow, mBookMark, mSendButton, mTopArrow;
    private MaterialButton mBookMarksButton, mClearButton, mHistoryButton, mInfoButton, mSaveButton, mSearchButton, mSettingsButton;
    private TextInputEditText mSearchWord;
    private RecyclerView mRecyclerViewOutput;
    private ShizukuShell mShizukuShell = null;
    private boolean mExit;
    private final Handler mHandler = new Handler();
    private int mPosition = 1;
    private List<String> mHistory = null, mResult = null;
    private String mCommandShared = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments == null) return;

        mCommandShared = arguments.getString("command");
    }

    @SuppressLint("SetTextI18n")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View mRootView = inflater.inflate(R.layout.fragment_ashell, container, false);

        mCommand = mRootView.findViewById(R.id.shell_command);
        mSearchWord = mRootView.findViewById(R.id.search_word);
        mSaveButton = mRootView.findViewById(R.id.save_button);
        MaterialCardView mSendCard = mRootView.findViewById(R.id.send_card);
        mBottomArrow = mRootView.findViewById(R.id.bottom);
        mClearButton = mRootView.findViewById(R.id.clear);
        mHistoryButton = mRootView.findViewById(R.id.history);
        mInfoButton = mRootView.findViewById(R.id.info);
        mSettingsButton = mRootView.findViewById(R.id.settings);
        mSearchButton = mRootView.findViewById(R.id.search);
        mBookMark = mRootView.findViewById(R.id.bookmark);
        mBookMarksButton = mRootView.findViewById(R.id.bookmarks);
        mSendButton = mRootView.findViewById(R.id.send);
        mTopArrow = mRootView.findViewById(R.id.top);
        mRecyclerViewOutput = mRootView.findViewById(R.id.recycler_view_output);
        mRecyclerViewOutput.setLayoutManager(new LinearLayoutManager(requireActivity()));

        mCommand.requestFocus();

        if (mCommandShared != null) {
            mCommand.setText(mCommandShared);
            mBookMark.setVisibility(View.VISIBLE);
            mBookMark.setImageDrawable(Utils.getDrawable(Utils.isBookmarked(mCommandShared, requireActivity()) ? R.drawable.ic_starred : R.drawable.ic_star, requireActivity()));
            mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_send, requireActivity()));
            mBookMark.setOnClickListener(v -> bookMark(mCommandShared));
        }

        mBookMarksButton.setEnabled(!Utils.getBookmarks(requireActivity()).isEmpty());

        mCommand.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @SuppressLint("SetTextI18n")
            @Override
            public void afterTextChanged(Editable s) {
                if (s.toString().contains("\n")) {
                    if (!s.toString().endsWith("\n")) {
                        mCommand.setText(s.toString().replace("\n", ""));
                    }
                    initializeShell(requireActivity());
                } else {
                    if (mShizukuShell != null && mShizukuShell.isBusy()) {
                        return;
                    }
                    RecyclerView mRecyclerViewCommands = mRootView.findViewById(R.id.recycler_view_commands);
                    if (!s.toString().trim().isEmpty()) {
                        mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_send, requireActivity()));
                        mSendButton.setColorFilter(Utils.getColor(R.color.colorWhite, requireActivity()));
                        mBookMark.setImageDrawable(Utils.getDrawable(Utils.isBookmarked(s.toString().trim(), requireActivity()) ? R.drawable.ic_starred : R.drawable.ic_star, requireActivity()));
                        mBookMark.setVisibility(View.VISIBLE);
                        mBookMark.setOnClickListener(v -> bookMark(s.toString().trim()));
                        new Handler(Looper.getMainLooper()).post(() -> {
                            CommandsAdapter mCommandsAdapter;
                            if (s.toString().contains(" ") && s.toString().contains(".")) {
                                String[] splitCommands =  {
                                        s.toString().substring(0, lastIndexOf(s.toString(), ".")), s.toString().substring(lastIndexOf(s.toString(), "."))
                                };

                                String packageNamePrefix;
                                if (splitCommands[0].contains(" ")) {
                                    packageNamePrefix = splitPrefix(splitCommands[0], 1);
                                } else {
                                    packageNamePrefix = splitCommands[0];
                                }

                                mCommandsAdapter = new CommandsAdapter(Commands.getPackageInfo(packageNamePrefix + "."));
                                mRecyclerViewCommands.setLayoutManager(new LinearLayoutManager(requireActivity()));
                                mRecyclerViewCommands.setAdapter(mCommandsAdapter);
                                mRecyclerViewCommands.setVisibility(View.VISIBLE);
                                mCommandsAdapter.setOnItemClickListener((command, v) -> {
                                    mCommand.setText(splitCommands[0].contains(" ") ? splitPrefix(splitCommands[0], 0) + " " + command : command);
                                    mCommand.setSelection(mCommand.getText().length());
                                    mRecyclerViewCommands.setVisibility(View.GONE);
                                });
                            } else {
                                mCommandsAdapter = new CommandsAdapter(Commands.getCommand(s.toString()));
                                mRecyclerViewCommands.setLayoutManager(new LinearLayoutManager(requireActivity()));
                                mRecyclerViewCommands.setAdapter(mCommandsAdapter);
                                mRecyclerViewCommands.setVisibility(View.VISIBLE);
                                mCommandsAdapter.setOnItemClickListener((command, v) -> {
                                    if (command.contains(" <")) {
                                        mCommand.setText(command.split("<")[0]);
                                    } else {
                                        mCommand.setText(command);
                                    }
                                    mCommand.setSelection(mCommand.getText().length());
                                });
                            }
                        });
                    } else {
                        mBookMark.setVisibility(View.GONE);
                        mRecyclerViewCommands.setVisibility(View.GONE);
                        mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_help, requireActivity()));
                        mSendButton.setColorFilter(Utils.getColorAccent(requireActivity()));
                    }
                }
            }
        });

        mSendCard.setOnClickListener(v -> {
            if (mShizukuShell != null && mShizukuShell.isBusy()) {
                mShizukuShell.destroy();
                mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_help, requireActivity()));
                mSendButton.setColorFilter(Utils.getColorAccent(requireActivity()));
            } else if (mCommand.getText() == null || mCommand.getText().toString().trim().isEmpty()) {
                Intent examples = new Intent(requireActivity(), ExamplesActivity.class);
                startActivity(examples);
            } else {
                initializeShell(requireActivity());
            }
        });

        mInfoButton.setOnClickListener(v -> {
            LayoutInflater mLayoutInflator = LayoutInflater.from(v.getContext());
            View aboutLayout = mLayoutInflator.inflate(R.layout.layout_about, null);
            MaterialTextView mAppTile = aboutLayout.findViewById(R.id.title);
            mAppTile.setText(v.getContext().getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);

            new MaterialAlertDialogBuilder(v.getContext())
                    .setView(aboutLayout)
                    .setPositiveButton(R.string.cancel, (dialogInterface, i) -> {
                    }).show();
        });

        mSettingsButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), mSettingsButton);
            Menu menu = popupMenu.getMenu();
            menu.add(Menu.NONE, 0, Menu.NONE, R.string.shizuku_about).setIcon(R.drawable.ic_info_outlined);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Utils.isDarkTheme(requireActivity()) && (mShizukuShell == null || !mShizukuShell.isBusy())) {
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.amoled_black).setIcon(R.drawable.ic_amoled_theme).setCheckable(true)
                        .setChecked(Utils.getBoolean("amoledTheme", false, requireActivity()));
            }
            menu.add(Menu.NONE, 2, Menu.NONE, R.string.examples).setIcon(R.drawable.ic_help);
            popupMenu.setForceShowIcon(true);
            popupMenu.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 0) {
                    Utils.loadShizukuWeb(requireActivity());
                } else if (item.getItemId() == 1) {
                    Utils.saveBoolean("amoledTheme", !Utils.getBoolean("amoledTheme", false, requireActivity()), requireActivity());
                    Intent mainActivity = new Intent(requireActivity(), aShellActivity.class);
                    mainActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(mainActivity);
                    requireActivity().finish();
                } else if (item.getItemId() == 2) {
                    Intent examples = new Intent(requireActivity(), ExamplesActivity.class);
                    startActivity(examples);
                }
                return false;
            });
            popupMenu.show();
        });

        mClearButton.setOnClickListener(v -> {
            if (mResult == null) return;
            if (Utils.getBoolean("clearAllMessage", true, requireActivity())) {
                new MaterialAlertDialogBuilder(requireActivity())
                        .setIcon(R.mipmap.ic_launcher)
                        .setTitle(getString(R.string.app_name))
                        .setMessage(getString(R.string.clear_all_message))
                        .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                        })
                        .setPositiveButton(getString(R.string.yes), (dialogInterface, i) -> {
                            Utils.saveBoolean("clearAllMessage", false, requireActivity());
                            clearAll();
                        }).show();
            } else {
                clearAll();
            }
        });

        mSearchButton.setOnClickListener(v -> {
            mHistoryButton.setVisibility(View.GONE);
            mClearButton.setVisibility(View.GONE);
            mBookMarksButton.setVisibility(View.GONE);
            mInfoButton.setVisibility(View.GONE);
            mSettingsButton.setVisibility(View.GONE);
            mSearchButton.setVisibility(View.GONE);
            mSearchWord.setVisibility(View.VISIBLE);
            mSearchWord.requestFocus();
            mCommand.setText(null);
            mCommand.setHint(null);
        });

        mSearchWord.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
            @Override
            public void afterTextChanged(Editable s) {
                if (s == null || s.toString().trim().isEmpty()) {
                    updateUI(mResult);
                } else {
                    List<String> mResultSorted = new ArrayList<>();
                    for (int i = mPosition; i < mResult.size(); i++) {
                        if (mResult.get(i).toLowerCase().contains(s.toString().toLowerCase())) {
                            mResultSorted.add(mResult.get(i));
                        }
                    }
                    updateUI(mResultSorted);
                }
            }
        });

        mBookMarksButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), mCommand);
            Menu menu = popupMenu.getMenu();
            for (int i = 0; i < Utils.getBookmarks(requireActivity()).size(); i++) {
                menu.add(Menu.NONE, i, Menu.NONE, Utils.getBookmarks(requireActivity()).get(i));
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                mCommand.setText(Utils.getBookmarks(requireActivity()).get(item.getItemId()));
                mCommand.setSelection(mCommand.getText().length());
                return false;
            });
            popupMenu.show();
        });

        mHistoryButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(requireContext(), mCommand);
            Menu menu = popupMenu.getMenu();
            for (int i = 0; i < getRecentCommands().size(); i++) {
                menu.add(Menu.NONE, i, Menu.NONE, getRecentCommands().get(i));
            }
            popupMenu.setOnMenuItemClickListener(item -> {
                mCommand.setText(getRecentCommands().get(item.getItemId()));
                mCommand.setSelection(mCommand.getText().length());
                return false;
            });
            popupMenu.show();
        });

        mSaveButton.setOnClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (int i = mPosition; i < mResult.size(); i++) {
                if (!mResult.get(i).equals("aShell: Finish") && !mResult.get(i).equals("<i></i>")) {
                    sb.append(mResult.get(i)).append("\n");
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, mHistory.get(mHistory.size() - 1)
                            .replace("/", "-").replace(" ", "") + ".txt");
                    values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = requireActivity().getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
                    OutputStream outputStream = requireActivity().getContentResolver().openOutputStream(Objects.requireNonNull(uri));
                    Objects.requireNonNull(outputStream).write(sb.toString().getBytes());
                    outputStream.close();
                } catch (IOException ignored) {
                }
            } else {
                if (requireActivity().checkCallingOrSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(requireActivity(), new String[] {
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    }, 0);
                    return;
                }
                Utils.create(sb.toString(), new File(Environment.DIRECTORY_DOWNLOADS, mHistory.get(mHistory.size() - 1)
                        .replace("/", "-").replace(" ", "") + ".txt"));
            }
            new MaterialAlertDialogBuilder(requireActivity())
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(getString(R.string.app_name))
                    .setMessage(getString(R.string.shell_output_saved_message, Environment.DIRECTORY_DOWNLOADS))
                    .setPositiveButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    }).show();
        });

        mTopArrow.setOnClickListener(v -> mRecyclerViewOutput.scrollToPosition(0));

        mBottomArrow.setOnClickListener(v -> mRecyclerViewOutput.scrollToPosition(Objects.requireNonNull(
                mRecyclerViewOutput.getAdapter()).getItemCount() - 1));

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (mResult != null && !mResult.isEmpty() && !mResult.get(mResult.size() - 1).equals("aShell: Finish")) {
                updateUI(mResult);
            }
        }, 0, 250, TimeUnit.MILLISECONDS);

        requireActivity().getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (mSearchWord.getVisibility() == View.VISIBLE) {
                    hideSearchBar();
                } else if (mShizukuShell != null && mShizukuShell.isBusy()) {
                    new MaterialAlertDialogBuilder(requireActivity())
                            .setCancelable(false)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(getString(R.string.app_name))
                            .setMessage(getString(R.string.process_destroy_message))
                            .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                            })
                            .setPositiveButton(getString(R.string.yes), (dialogInterface, i) -> mShizukuShell.destroy()
                            ).show();
                } else if (mExit) {
                    mExit = false;
                    requireActivity().finish();
                } else {
                    Utils.toast(getString(R.string.press_back), requireActivity()).show();
                    mExit = true;
                    mHandler.postDelayed(() -> mExit = false, 2000);
                }
            }
        });

        return mRootView;
    }

    private int lastIndexOf(String s, String splitTxt) {
        return s.lastIndexOf(splitTxt);
    }

    private List<String> getRecentCommands() {
        List<String> mRecentCommands = new ArrayList<>(mHistory);
        Collections.reverse(mRecentCommands);
        return mRecentCommands;
    }

    private String splitPrefix(String s, int i) {
        String[] splitPrefix = {
                s.substring(0, lastIndexOf(s, " ")), s.substring(lastIndexOf(s, " "))
        };
        return splitPrefix[i].trim();
    }

    private void clearAll() {
        if (mShizukuShell != null) mShizukuShell.destroy();
        mResult = null;
        mRecyclerViewOutput.setAdapter(null);
        mSearchButton.setEnabled(false);
        mSaveButton.setVisibility(View.GONE);
        mClearButton.setEnabled(false);
        mCommand.setHint(getString(R.string.command_hint));
        mTopArrow.setVisibility(View.GONE);
        mBottomArrow.setVisibility(View.GONE);
        if (!mCommand.isFocused()) mCommand.requestFocus();
    }

    private void bookMark(String string) {
        if (Utils.isBookmarked(string, requireActivity())) {
            Utils.deleteFromBookmark(string, requireActivity());
            Utils.toast(getString(R.string.bookmark_removed_message, string), requireActivity()).show();
        } else {
            Utils.addToBookmark(string, requireActivity());
            Utils.toast(getString(R.string.bookmark_added_message, string), requireActivity()).show();
        }
        mBookMark.setImageDrawable(Utils.getDrawable(Utils.isBookmarked(string, requireActivity()) ? R.drawable.ic_starred : R.drawable.ic_star, requireActivity()));
        mBookMarksButton.setEnabled(!Utils.getBookmarks(requireActivity()).isEmpty());
    }

    private void hideSearchBar() {
        mSearchWord.setText(null);
        mSearchWord.setVisibility(View.GONE);
        if (!mCommand.isFocused()) mCommand.requestFocus();
        mBookMarksButton.setVisibility(View.VISIBLE);
        mInfoButton.setVisibility(View.VISIBLE);
        mSettingsButton.setVisibility(View.VISIBLE);
        mHistoryButton.setVisibility(View.VISIBLE);
        mClearButton.setVisibility(View.VISIBLE);
        mSearchButton.setVisibility(View.VISIBLE);
    }

    private void initializeShell(Activity activity) {
        if (mCommand.getText() == null || mCommand.getText().toString().trim().isEmpty()) {
            return;
        }
        if (mShizukuShell != null && mShizukuShell.isBusy()) {
            new MaterialAlertDialogBuilder(activity)
                    .setCancelable(false)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(getString(R.string.app_name))
                    .setMessage(getString(R.string.app_working_message))
                    .setPositiveButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    }).show();
            return;
        }
        runShellCommand(mCommand.getText().toString().replace("\n", ""), activity);
    }

    private void runShellCommand(String command, Activity activity) {
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        mCommand.setText(null);
        mCommand.setHint(null);
        mCommand.clearFocus();
        if (mSearchWord.getVisibility() == View.VISIBLE) {
            mSearchWord.setText(null);
            mSearchWord.setVisibility(View.GONE);
        }

        if (mTopArrow.getVisibility() == View.VISIBLE) {
            mTopArrow.setVisibility(View.GONE);
            mBottomArrow.setVisibility(View.GONE);
        }

        String finalCommand;
        if (command.startsWith("adb shell ")) {
            finalCommand = command.replace("adb shell ", "");
        } else if (command.startsWith("adb -d shell ")) {
            finalCommand = command.replace("adb -d shell ", "");
        } else {
            finalCommand = command;
        }

        if (finalCommand.equals("clear")) {
            if (mResult != null) {
                clearAll();
            }
            return;
        }

        if (finalCommand.equals("exit")) {
            new MaterialAlertDialogBuilder(activity)
                    .setCancelable(false)
                    .setIcon(R.mipmap.ic_launcher)
                    .setTitle(getString(R.string.app_name))
                    .setMessage(getString(R.string.quit_app_message))
                    .setNegativeButton(getString(R.string.cancel), (dialogInterface, i) -> {
                    })
                    .setPositiveButton(getString(R.string.quit), (dialogInterface, i) -> activity.finish()).show();
            return;
        }

        if (finalCommand.startsWith("su")) {
            Utils.toast(getString(R.string.su_warning_message), requireActivity()).show();
            return;
        }

        if (mHistory == null) {
            mHistory = new ArrayList<>();
        }
        mHistory.add(finalCommand);

        mSaveButton.setVisibility(View.GONE);
        mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_stop, requireActivity()));
        mSendButton.setColorFilter(Utils.getColor(R.color.colorRed, requireActivity()));

        mHistoryButton.setEnabled(false);
        mBookMarksButton.setEnabled(false);
        mClearButton.setEnabled(false);
        mSearchButton.setEnabled(false);
        mInfoButton.setEnabled(false);
        mSettingsButton.setEnabled(false);

        String mTitleText = "<font color=\"" + Utils.getColorAccent(activity) + "\">shell@" + Utils.getDeviceName() + "</font># <i>" + finalCommand + "</i>";

        if (mResult == null) {
            mResult = new ArrayList<>();
        }
        mResult.add(mTitleText);

        ExecutorService mExecutors = Executors.newSingleThreadExecutor();
        mExecutors.execute(() -> {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                mPosition = mResult.size();
                mShizukuShell = new ShizukuShell(mResult, finalCommand);
                mShizukuShell.exec();
                try {
                    TimeUnit.MILLISECONDS.sleep(250);
                } catch (InterruptedException ignored) {}
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    if (mHistory != null && !mHistory.isEmpty() && !mHistoryButton.isEnabled()) {
                        mHistoryButton.setEnabled(true);
                    }
                    mInfoButton.setEnabled(true);
                    mSettingsButton.setEnabled(true);
                    mBookMarksButton.setEnabled(!Utils.getBookmarks(requireActivity()).isEmpty());
                    if (mResult != null && !mResult.isEmpty()) {
                        mClearButton.setEnabled(true);
                        mSaveButton.setVisibility(View.VISIBLE);
                        mSearchButton.setEnabled(true);
                        if (mResult.size() > 25) {
                            mTopArrow.setVisibility(View.VISIBLE);
                            mBottomArrow.setVisibility(View.VISIBLE);
                        }
                        mResult.add("<i></i>");
                        mResult.add("aShell: Finish");
                    }
                } else {
                    new MaterialAlertDialogBuilder(activity)
                            .setCancelable(false)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(getString(R.string.app_name))
                            .setMessage(getString(R.string.shizuku_access_denied_message))
                            .setNegativeButton(getString(R.string.quit), (dialogInterface, i) -> activity.finish())
                            .setPositiveButton(getString(R.string.request_permission), (dialogInterface, i) -> Shizuku.requestPermission(0)
                            ).show();
                }
                if (mCommand.getText() == null || mCommand.getText().toString().trim().isEmpty()) {
                    mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_help, requireActivity()));
                    mSendButton.setColorFilter(Utils.getColorAccent(requireActivity()));
                } else {
                    mSendButton.setImageDrawable(Utils.getDrawable(R.drawable.ic_send, requireActivity()));
                    mSendButton.setColorFilter(Utils.getColor(R.color.colorWhite, requireActivity()));
                }
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                if (!mCommand.isFocused()) mCommand.requestFocus();
            });
            if (!mExecutors.isShutdown()) mExecutors.shutdown();
        });
    }

    private void updateUI(List<String> data) {
        if (data == null || data.isEmpty()) return;
        List<String> mData = new ArrayList<>();
        try {
            for (String result : data) {
                if (!result.trim().isEmpty() && !result.equals("aShell: Finish")) {
                    mData.add(result);
                }
            }
        } catch (ConcurrentModificationException ignored) {
        }
        ExecutorService mExecutors = Executors.newSingleThreadExecutor();
        mExecutors.execute(() -> {
            ShellOutputAdapter mShellOutputAdapter = new ShellOutputAdapter(mData);
            new Handler(Looper.getMainLooper()).post(() -> {
                mRecyclerViewOutput.setAdapter(mShellOutputAdapter);
                mRecyclerViewOutput.scrollToPosition(mData.size() - 1);
            });
            if (!mExecutors.isShutdown()) mExecutors.shutdown();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mShizukuShell != null) mShizukuShell.destroy();
    }

}
