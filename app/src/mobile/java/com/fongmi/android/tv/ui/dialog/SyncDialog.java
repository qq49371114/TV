package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.event.ScanEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.ScanActivity;
import com.fongmi.android.tv.ui.adapter.DeviceAdapter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.github.catvod.net.OkHttp;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class SyncDialog extends BaseDialog implements DeviceAdapter.OnClickListener, ScanTask.Listener {

    private final FormBody.Builder body;
    private final OkHttpClient client;
    private DialogDeviceBinding binding;
    private DeviceAdapter adapter;
    private String type;

    public static SyncDialog create() {
        return new SyncDialog();
    }

    public SyncDialog() {
        client = OkHttp.client(Constant.TIMEOUT_SYNC);
        body = new FormBody.Builder();
    }

    public SyncDialog history() {
        body.add("device", Device.get().toString());
        body.add("config", Config.vod().toString());
        body.add("targets", App.gson().toJson(History.get()));
        return type("history");
    }

    public SyncDialog keep() {
        body.add("device", Device.get().toString());
        body.add("targets", App.gson().toJson(Keep.getVod()));
        body.add("configs", App.gson().toJson(Config.findUrls()));
        return type("keep");
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof BottomSheetDialogFragment) return;
        show(activity.getSupportFragmentManager(), null);
    }

    private SyncDialog type(String type) {
        this.type = type;
        return this;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        binding.mode.setVisibility(View.VISIBLE);
        EventBus.getDefault().register(this);
        setRecyclerView();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.mode.setOnClickListener(v -> onMode());
        binding.scan.setOnClickListener(v -> onScan());
        binding.refresh.setOnClickListener(v -> onRefresh());
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter = new DeviceAdapter(this));
    }

    private void getDevice() {
        adapter.addAll(Device.getAll());
        if (adapter.getItemCount() == 0) App.post(this::onRefresh, 1000);
    }

    private void onMode() {
        if (binding.mode.getTag().toString().equals("0")) {
            binding.mode.setTag("1");
            binding.mode.setImageResource(R.drawable.ic_cast_upload);
        } else if (binding.mode.getTag().toString().equals("1")) {
            binding.mode.setTag("2");
            binding.mode.setImageResource(R.drawable.ic_cast_download);
        } else if (binding.mode.getTag().toString().equals("2")) {
            binding.mode.setTag("0");
            binding.mode.setImageResource(R.drawable.ic_cast_sync);
        }
    }

    private void onScan() {
        ScanActivity.start(getActivity());
    }

    private void onRefresh() {
        ScanTask.create(this).start(adapter.getIps());
        adapter.clear();
    }

    private void onSuccess() {
        dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onScanEvent(ScanEvent event) {
        ScanTask.create(this).start(event.getAddress());
    }

    @Override
    public void onFind(List<Device> devices) {
        if (devices.size() > 0) adapter.addAll(devices);
    }

    @Override
    public void onItemClick(Device item) {
        OkHttp.newCall(client, String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=%s", item.getIp(), binding.mode.getTag().toString(), type), body.build()).enqueue(getCallback());
    }

    @Override
    public boolean onLongClick(Device item) {
        String mode = binding.mode.getTag().toString();
        if (mode.equals("0")) return false;
        if (mode.equals("2") && type.equals("keep")) Keep.deleteAll();
        if (mode.equals("2") && type.equals("history")) History.delete(VodConfig.getCid());
        OkHttp.newCall(client, String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=%s&force=true", item.getIp(), binding.mode.getTag().toString(), type), body.build()).enqueue(getCallback());
        return true;
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                App.post(() -> onSuccess());
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                App.post(() -> Notify.show(e.getMessage()));
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
