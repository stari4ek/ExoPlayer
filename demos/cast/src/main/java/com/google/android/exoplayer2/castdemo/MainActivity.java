/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.castdemo;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.graphics.ColorUtils;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.castdemo.DemoUtil.Sample;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;

/**
 * An activity that plays video using {@link SimpleExoPlayer} and {@link CastPlayer}.
 */
public class MainActivity extends AppCompatActivity implements OnClickListener,
    PlayerManager.QueuePositionListener {

  private SimpleExoPlayerView simpleExoPlayerView;
  private PlaybackControlView castControlView;
  private PlayerManager playerManager;
  private MediaQueueAdapter listAdapter;
  private CastContext castContext;

  // Activity lifecycle methods.

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Getting the cast context later than onStart can cause device discovery not to take place.
    castContext = CastContext.getSharedInstance(this);

    setContentView(R.layout.main_activity);

    simpleExoPlayerView = findViewById(R.id.player_view);
    simpleExoPlayerView.requestFocus();

    castControlView = findViewById(R.id.cast_control_view);

    RecyclerView sampleList = findViewById(R.id.sample_list);
    ItemTouchHelper helper = new ItemTouchHelper(new RecyclerViewCallback());
    helper.attachToRecyclerView(sampleList);
    sampleList.setLayoutManager(new LinearLayoutManager(this));
    sampleList.setHasFixedSize(true);
    listAdapter = new MediaQueueAdapter();
    sampleList.setAdapter(listAdapter);

    findViewById(R.id.add_sample_button).setOnClickListener(this);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    super.onCreateOptionsMenu(menu);
    getMenuInflater().inflate(R.menu.menu, menu);
    CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.media_route_menu_item);
    return true;
  }

  @Override
  public void onResume() {
    super.onResume();
    playerManager = PlayerManager.createPlayerManager(this, simpleExoPlayerView, castControlView,
        this, castContext);
  }

  @Override
  public void onPause() {
    super.onPause();
    playerManager.release();
    playerManager = null;
  }

  // Activity input.

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    // If the event was not handled then see if the player view can handle it.
    return super.dispatchKeyEvent(event) || playerManager.dispatchKeyEvent(event);
  }

  @Override
  public void onClick(View view) {
    new AlertDialog.Builder(this).setTitle(R.string.sample_list_dialog_title)
        .setView(buildSampleListView()).setPositiveButton(android.R.string.ok, null).create()
        .show();
  }

  // PlayerManager.QueuePositionListener implementation.

  @Override
  public void onQueuePositionChanged(int previousIndex, int newIndex) {
    if (previousIndex != C.INDEX_UNSET) {
      listAdapter.notifyItemChanged(previousIndex);
    }
    if (newIndex != C.INDEX_UNSET) {
      listAdapter.notifyItemChanged(newIndex);
    }
  }

  // Internal methods.

  private View buildSampleListView() {
    View dialogList = getLayoutInflater().inflate(R.layout.sample_list, null);
    ListView sampleList = dialogList.findViewById(R.id.sample_list);
    sampleList.setAdapter(new SampleListAdapter(this));
    sampleList.setOnItemClickListener(new OnItemClickListener() {

      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        playerManager.addItem(DemoUtil.SAMPLES.get(position));
        listAdapter.notifyItemInserted(playerManager.getMediaQueueSize() - 1);
      }

    });
    return dialogList;
  }

  // Internal classes.

  private class QueueItemViewHolder extends RecyclerView.ViewHolder implements OnClickListener {

    public final TextView textView;

    public QueueItemViewHolder(TextView textView) {
      super(textView);
      this.textView = textView;
      textView.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
      playerManager.selectQueueItem(getAdapterPosition());
    }

  }

  private class MediaQueueAdapter extends RecyclerView.Adapter<QueueItemViewHolder> {

    @Override
    public QueueItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
      TextView v = (TextView) LayoutInflater.from(parent.getContext())
          .inflate(android.R.layout.simple_list_item_1, parent, false);
      return new QueueItemViewHolder(v);
    }

    @Override
    public void onBindViewHolder(QueueItemViewHolder holder, int position) {
      TextView view = holder.textView;
      view.setText(playerManager.getItem(position).name);
      // TODO: Solve coloring using the theme's ColorStateList.
      view.setTextColor(ColorUtils.setAlphaComponent(view.getCurrentTextColor(),
           position == playerManager.getCurrentItemIndex() ? 255 : 100));
    }

    @Override
    public int getItemCount() {
      return playerManager.getMediaQueueSize();
    }

  }

  private class RecyclerViewCallback extends ItemTouchHelper.SimpleCallback {

    private int draggingFromPosition;
    private int draggingToPosition;

    public RecyclerViewCallback() {
      super(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.START | ItemTouchHelper.END);
      draggingFromPosition = C.INDEX_UNSET;
      draggingToPosition = C.INDEX_UNSET;
    }

    @Override
    public boolean onMove(RecyclerView list, RecyclerView.ViewHolder origin,
        RecyclerView.ViewHolder target) {
      int fromPosition = origin.getAdapterPosition();
      int toPosition = target.getAdapterPosition();
      if (draggingFromPosition == C.INDEX_UNSET) {
        // A drag has started, but changes to the media queue will be reflected in clearView().
        draggingFromPosition = fromPosition;
      }
      draggingToPosition = toPosition;
      listAdapter.notifyItemMoved(fromPosition, toPosition);
      return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
      int position = viewHolder.getAdapterPosition();
      if (playerManager.removeItem(position)) {
        listAdapter.notifyItemRemoved(position);
      }
    }

    @Override
    public void clearView(RecyclerView recyclerView, ViewHolder viewHolder) {
      super.clearView(recyclerView, viewHolder);
      if (draggingFromPosition != C.INDEX_UNSET) {
        // A drag has ended. We reflect the media queue change in the player.
        if (!playerManager.moveItem(draggingFromPosition, draggingToPosition)) {
          // The move failed. The entire sequence of onMove calls since the drag started needs to be
          // invalidated.
          listAdapter.notifyDataSetChanged();
        }
      }
      draggingFromPosition = C.INDEX_UNSET;
      draggingToPosition = C.INDEX_UNSET;
    }

  }

  private static final class SampleListAdapter extends ArrayAdapter<Sample> {

    public SampleListAdapter(Context context) {
      super(context, android.R.layout.simple_list_item_1, DemoUtil.SAMPLES);
    }

  }

}
