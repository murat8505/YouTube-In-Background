package com.teocci.ytinbg.adapters;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.nhaarman.listviewanimations.itemmanipulation.swipedismiss.undo.UndoAdapter;
import com.nhaarman.listviewanimations.util.Swappable;
import com.squareup.picasso.Picasso;
import com.teocci.ytinbg.R;
import com.teocci.ytinbg.database.YouTubeSqlDb;
import com.teocci.ytinbg.model.YouTubeVideo;
import com.teocci.ytinbg.ui.DownloadActivity;
import com.teocci.ytinbg.utils.Config;
import com.teocci.ytinbg.utils.LogHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

/**
 * Custom ArrayAdapter which enables setup of a videoList view row views
 * Created by teocci on 8.2.16..
 */
public class VideosAdapter extends RecyclerView.Adapter<VideosAdapter.VideoViewHolder> implements Swappable, UndoAdapter
{
    private static final String TAG = LogHelper.makeLogTag(VideosAdapter.class);

    private Activity context;
    private final List<YouTubeVideo> videoList;
    private List<String> favorites;
    private boolean isFavoriteList;

    private AdapterView.OnItemClickListener onItemClickListener;

    public VideosAdapter(Activity context, boolean isFavoriteList)
    {
        this.videoList = new ArrayList<>();
        this.favorites = new ArrayList<>();
        this.context = context;
        this.isFavoriteList = isFavoriteList;
    }

    public VideosAdapter(Activity context, List<YouTubeVideo> videoList, boolean isFavoriteList)
    {
        this.videoList = videoList;
        this.favorites = new ArrayList<>();
        this.context = context;
        this.isFavoriteList = isFavoriteList;
    }

    @Override
    public VideoViewHolder onCreateViewHolder(ViewGroup container, int viewType)
    {
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        View root = inflater.inflate(R.layout.video_item, container, false);

        return new VideoViewHolder(root, this);
    }

    @Override
    public void onBindViewHolder(VideoViewHolder videoViewHolder, final int position)
    {
        final YouTubeVideo searchResult = videoList.get(position);

        Picasso.with(context)
                .load(searchResult.getThumbnailURL())
                .centerCrop()
                .fit()
                .into(videoViewHolder.thumbnail);
        videoViewHolder.title.setText(searchResult.getTitle());
        videoViewHolder.duration.setText(searchResult.getDuration());
        videoViewHolder.viewCount.setText(formatViewCount(searchResult.getViewCount()));

        //set checked if exists in database
        boolean isFavorite = YouTubeSqlDb.getInstance().videos(YouTubeSqlDb.VIDEOS_TYPE
                .FAVORITE).checkIfExists(searchResult.getId());
        if (isFavorite) favorites.add(searchResult.getId());
        videoViewHolder.checkBoxFavorite.setChecked(isFavorite);

        videoViewHolder.checkBoxFavorite.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener()
        {
            public void onCheckedChanged(CompoundButton btn, boolean isChecked)
            {
                if (!isChecked) favorites.remove(searchResult.getId());
            }
        });

        videoViewHolder.checkBoxFavorite.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (((CheckBox) v).isChecked()) {
                    YouTubeSqlDb.getInstance().videos(YouTubeSqlDb.VIDEOS_TYPE.FAVORITE).create
                            (searchResult);
                } else {
                    YouTubeSqlDb.getInstance().videos(YouTubeSqlDb.VIDEOS_TYPE.FAVORITE).delete
                            (searchResult.getId());
                    if (isFavoriteList) {
                        videoList.remove(position);
                        notifyDataSetChanged();
                    }
                }
            }
        });

        videoViewHolder.imageButtonShare.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                doShareLink(searchResult.getTitle(), searchResult.getId());
            }
        });

        videoViewHolder.imageButtonDownload.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                doDownloadVideo(searchResult.getId());
            }
        });
    }

    @Override
    public long getItemId(int position)
    {
        if (position >= videoList.size()) return -1;
        return videoList.get(position).hashCode();
    }

    @Override
    public int getItemCount()
    {
        return videoList.size();
    }

    public YouTubeVideo getYouTubeVideos(int position)
    {
        if (position >= videoList.size()) return null;
        return videoList.get(position);
    }

    /**
     * A common adapter modification or reset mechanism. As with ListAdapter,
     * calling notifyDataSetChanged() will trigger the RecyclerView to update
     * the view. However, this method will not trigger any of the RecyclerView
     * animation features.
     */
    public void setYouTubeVideos(ArrayList<YouTubeVideo> youTubeVideos)
    {
        videoList.clear();
        videoList.addAll(youTubeVideos);

        notifyDataSetChanged();
    }

    /**
     * Inserting a new item at the head of the list. This uses a specialized
     * RecyclerView method, notifyItemRemoved(), to trigger any enabled item
     * animations in addition to updating the view.
     */
    public void removeVideo(int position)
    {
        if (position >= videoList.size()) return;

        videoList.remove(position);
        notifyItemRemoved(position);
    }

    public void swapItems(int positionA, int positionB)
    {
        if (positionA > videoList.size()) return;
        if (positionB > videoList.size()) return;

        YouTubeVideo firstItem = videoList.get(positionA);

        videoList.set(positionA, videoList.get(positionB));
        videoList.set(positionB, firstItem);

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public View getUndoView(int i, @Nullable View convertView, @NonNull ViewGroup viewGroup)
    {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.undo_row, viewGroup, false);
        }
        return view;
    }

    @NonNull
    @Override
    public View getUndoClickView(@NonNull View view)
    {
        return view.findViewById(R.id.button_undo_row);
    }

    private static final NavigableMap<Long, String> suffixes = new TreeMap<>();

    static {
        suffixes.put(1_000L, " K");
    }

    private String formatViewCount(String viewCounts)
    {
        String[] split = viewCounts.split(" ");
        String[] segments = split[0].split(",");

        int count = segments.length;
        String suffix = count > 2 ? " M" : count > 1  ? " K" : "";
        count = count > 2 ? count - 2 : count > 1  ?  count - 1 :  count;
        String number = "";
        for (String segment : segments) {
            number += segment;
            if (count-- == 1) break;
            number += ",";
            Log.e(TAG, "segment: " + segment + " --> " + number);
        }

        Log.e(TAG, "number: " + number);

        return number + suffix + " " + split[1];
    }

    private String formatLong(long value)
    {
        //Long.MIN_VALUE == -Long.MIN_VALUE so we need an adjustment here
        if (value == Long.MIN_VALUE) return formatLong(Long.MIN_VALUE + 1);
        if (value < 0) return "-" + formatLong(-value);
        if (value < 1000) return Long.toString(value); //deal with easy case

        Map.Entry<Long, String> e = suffixes.floorEntry(value);
        Long divideBy = e.getKey();
        String suffix = e.getValue();

        long truncated = value / (divideBy / 10); //the number part of the output times 10
        boolean hasDecimal = truncated < 100 && (truncated / 10d) != (truncated / 10);
        return hasDecimal ? (truncated / 10d) + suffix : (truncated / 10) + suffix;
    }

    private void doShareLink(String text, String link)
    {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        Intent chooserIntent = Intent.createChooser(shareIntent, context.getResources().getString(R.string.share_image_button));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getResources().getString(R.string.app_name));
            shareIntent.putExtra(Intent.EXTRA_TEXT, text + " https://youtu.be/" + link);
        } else {

            shareIntent.putExtra(Intent.EXTRA_SUBJECT, context.getResources().getString(R.string.app_name));
            shareIntent.putExtra(Intent.EXTRA_TEXT, "https://youtu.be/" + link);
        }

//        chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooserIntent);
    }

    private void doDownloadVideo(String link)
    {
        Intent downloadIntent = new Intent(context, DownloadActivity.class);
        downloadIntent.putExtra(Config.YOUTUBE_LINK, "https://youtu.be/" + link);
        context.startActivity(downloadIntent);
    }

    public void setOnItemClickListener(AdapterView.OnItemClickListener onItemClickListener)
    {
        this.onItemClickListener = onItemClickListener;
    }

    private void onItemHolderClick(VideoViewHolder itemHolder)
    {
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(null, itemHolder.itemView,
                    itemHolder.getAdapterPosition(), itemHolder.getItemId());
        }
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    public static class VideoViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener
    {
        public ImageView thumbnail;
        public TextView title;
        public TextView duration;
        public TextView viewCount;
        public CheckBox checkBoxFavorite;
        public ImageButton imageButtonDownload;
        public ImageButton imageButtonShare;

        private VideosAdapter videosAdapter;

        public VideoViewHolder(View convertView, VideosAdapter videosAdapter)
        {
            super(convertView);
            convertView.setOnClickListener(this);

            this.videosAdapter = videosAdapter;

            thumbnail = (ImageView) convertView.findViewById(R.id.video_thumbnail);
            title = (TextView) convertView.findViewById(R.id.video_title);
            duration = (TextView) convertView.findViewById(R.id.video_duration);
            viewCount = (TextView) convertView.findViewById(R.id.views_number);
            checkBoxFavorite = (CheckBox) convertView.findViewById(R.id.image_button_favorite);
            imageButtonDownload = (ImageButton) convertView.findViewById(R.id.image_button_download);
            imageButtonShare = (ImageButton) convertView.findViewById(R.id.image_button_share);
        }

        @Override
        public void onClick(View v)
        {
            videosAdapter.onItemHolderClick(this);
        }
    }
}
