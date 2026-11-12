package com.ali.carsystem;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import android.os.AsyncTask;
import android.util.LruCache;

import java.util.ArrayList;
import java.util.List;

public final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
    public interface Click { void onClick(VideoItem item); }
    private static final LruCache<String, Bitmap> THUMB_CACHE = new LruCache<String, Bitmap>(3072) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };
    private final List<VideoItem> items = new ArrayList<>();
    private static int dp(View view,float value){return Math.round(value*view.getResources().getDisplayMetrics().density);}
    private final Click click;

    public VideoAdapter(Click listener) { click = listener; }
    public void setItems(List<VideoItem> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    @Override public Holder onCreateViewHolder(ViewGroup parent, int type) {
        LinearLayout row = new LinearLayout(parent.getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(parent,8), dp(parent,6), dp(parent,8), dp(parent,6));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE9182A3C);
        bg.setCornerRadius(dp(parent,12));
        bg.setStroke(dp(parent,1), 0xFF257EA0);
        row.setBackground(bg);

        ImageView thumb = new ImageView(parent.getContext());
        thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.setBackgroundColor(0xFF101A28);
        row.addView(thumb, new LinearLayout.LayoutParams(dp(parent,160), dp(parent,90)));

        LinearLayout texts = new LinearLayout(parent.getContext());
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);
        texts.setPadding(dp(parent,12), dp(parent,2), dp(parent,8), dp(parent,2));
        TextView title = new TextView(parent.getContext());
        title.setTextColor(Color.WHITE);
        title.setTextSize(14);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView author = new TextView(parent.getContext());
        author.setTextColor(0xFFB6D7E8);
        author.setTextSize(11);
        author.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        texts.addView(title, new LinearLayout.LayoutParams(-1, dp(parent,48)));
        texts.addView(author, new LinearLayout.LayoutParams(-1, dp(parent,24)));
        row.addView(texts, new LinearLayout.LayoutParams(0, dp(parent,90), 1));
        return new Holder(row, thumb, title, author);
    }

    @Override public void onBindViewHolder(Holder holder, int position) {
        VideoItem item = items.get(position);
        holder.title.setText(item.title);
        holder.author.setText(item.author == null || item.author.length() == 0 ? "قناة" : item.author);
        holder.itemView.setOnClickListener(v -> {
            int index = holder.getAdapterPosition();
            if (index != RecyclerView.NO_POSITION) click.onClick(items.get(index));
        });
        holder.load(item.thumbnail);
    }

    @Override public void onViewRecycled(Holder holder) {
        holder.clear();
        super.onViewRecycled(holder);
    }

    @Override public int getItemCount() { return items.size(); }

    final class Holder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final TextView title, author;
        String boundUrl;
        AsyncTask<String, Void, Bitmap> task;

        Holder(View view, ImageView image, TextView name, TextView creator) {
            super(view);
            thumbnail = image;
            title = name;
            author = creator;
        }

        void load(String url) {
            clear();
            boundUrl = url;
            thumbnail.setImageDrawable(null);
            if (url == null || url.length() == 0) return;
            Bitmap cached = THUMB_CACHE.get(url);
            if (cached != null) {
                thumbnail.setImageBitmap(cached);
                return;
            }
            task = new AsyncTask<String, Void, Bitmap>() {
                protected Bitmap doInBackground(String... values) {
                    return VideoApiClient.downloadThumbnail(itemView.getContext(), values[0]);
                }
                protected void onPostExecute(Bitmap bitmap) {
                    if (bitmap != null && url.equals(boundUrl)) {
                        THUMB_CACHE.put(url, bitmap);
                        thumbnail.setImageBitmap(bitmap);
                    }
                }
            }.execute(url);
        }

        void clear() {
            boundUrl = null;
            if (task != null) task.cancel(true);
            task = null;
            thumbnail.setImageDrawable(null);
        }
    }
}
