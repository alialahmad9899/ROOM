package com.ali.carsystem;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public final class VideoAdapter extends RecyclerView.Adapter<VideoAdapter.Holder> {
    public interface Click { void onClick(VideoItem item); }
    private final List<VideoItem> items=new ArrayList<>();
    private final Click click;
    public VideoAdapter(Click c){click=c;}
    public void setItems(List<VideoItem> values){items.clear();items.addAll(values);notifyDataSetChanged();}
    @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
        LinearLayout row=new LinearLayout(parent.getContext());row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.VERTICAL);row.setPadding(14,7,14,7);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xE9182A3C);bg.setCornerRadius(14);bg.setStroke(1,0xFF257EA0);row.setBackground(bg);
        TextView title=new TextView(parent.getContext());title.setTextColor(Color.WHITE);title.setTextSize(15);title.setTypeface(Typeface.DEFAULT_BOLD);
        TextView author=new TextView(parent.getContext());author.setTextColor(0xFFB6D7E8);author.setTextSize(11);
        row.addView(title,new LinearLayout.LayoutParams(-1,34));row.addView(author,new LinearLayout.LayoutParams(-1,22));
        return new Holder(row,title,author);
    }
    @Override public void onBindViewHolder(Holder h,int position){
        VideoItem item=items.get(position);h.title.setText(item.title);h.author.setText(item.author);
        h.itemView.setOnClickListener(v->click.onClick(item));
    }
    @Override public int getItemCount(){return items.size();}
    static final class Holder extends RecyclerView.ViewHolder{
        final TextView title,author;Holder(View v,TextView t,TextView a){super(v);title=t;author=a;}
    }
}
