package com.ali.carsystem;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

public final class YouTubeSearchActivity extends BaseActivity {
    private EditText query;private TextView status;private RecyclerView list;private SearchAdapter adapter;private AsyncTask<String,Void,List<VideoApi.Item>> task;
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));root.setBackgroundColor(0xFF060D16);
        TextView title=new TextView(this);title.setText("يوتيوب لايت");title.setTextColor(Color.WHITE);title.setTextSize(22);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);root.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(0xFF111111);query.setHintTextColor(0xFF666666);query.setHint("ابحث عن فيديو");query.setTextSize(17);query.setPadding(dp(14),0,dp(14),0);
        GradientDrawable white=new GradientDrawable();white.setColor(Color.WHITE);white.setCornerRadius(dp(12));query.setBackground(white);row.addView(query,new LinearLayout.LayoutParams(0,dp(50),1));
        Button search=new Button(this);search.setText("بحث");row.addView(search,new LinearLayout.LayoutParams(dp(90),dp(50)));root.addView(row);
        status=new TextView(this);status.setText("اكتب عبارة البحث بالعربية أو الإنجليزية");status.setTextColor(0xFFB8D1E3);status.setGravity(Gravity.CENTER);root.addView(status,new LinearLayout.LayoutParams(-1,dp(36)));
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new SearchAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        search.setOnClickListener(v->doSearch());query.setOnEditorActionListener((v,a,e)->{doSearch();return true;});setContentView(root);
    }
    private void doSearch(){
        String q=query.getText().toString().trim();if(q.length()<2){query.setError("أدخل كلمتين أو أكثر");return;}
        if(task!=null)task.cancel(true);((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);
        status.setText("جارٍ البحث في الخوادم العامة المتاحة...");adapter.set(new ArrayList<>());
        task=new AsyncTask<String,Void,List<VideoApi.Item>>(){Exception error;
            protected List<VideoApi.Item> doInBackground(String...p){try{return VideoApi.search(getApplicationContext(),p[0]);}catch(Exception e){error=e;return null;}}
            protected void onPostExecute(List<VideoApi.Item> result){if(isFinishing())return;if(result==null){status.setText(error==null?"تعذر إكمال البحث":error.getMessage());return;}status.setText("نتائج البحث: "+result.size());adapter.set(result);}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,q);
    }
    @Override protected void onDestroy(){if(task!=null)task.cancel(true);super.onDestroy();}
    private final class SearchAdapter extends RecyclerView.Adapter<Row>{
        private List<VideoApi.Item> items=new ArrayList<>();
        void set(List<VideoApi.Item> x){items=x;notifyDataSetChanged();}
        @Override public Row onCreateViewHolder(ViewGroup p,int t){LinearLayout box=new LinearLayout(YouTubeSearchActivity.this);box.setGravity(Gravity.CENTER_VERTICAL);box.setPadding(dp(12),dp(8),dp(12),dp(8));box.setBackground(round());TextView text=new TextView(YouTubeSearchActivity.this);text.setTextColor(Color.WHITE);text.setTextSize(15);box.addView(text,new LinearLayout.LayoutParams(-1,dp(60)));return new Row(box,text);}
        @Override public void onBindViewHolder(Row h,int i){VideoApi.Item v=items.get(i);h.text.setText(v.title+"\n"+v.author);h.itemView.setOnClickListener(w->{Intent intent=new Intent(YouTubeSearchActivity.this,VideoPlayerActivity.class);intent.putExtra("id",v.id);intent.putExtra("title",v.title);intent.putExtra("backend",v.backend);intent.putExtra("base",v.base);startActivity(intent);});}
        @Override public int getItemCount(){return items.size();}
    }
    private GradientDrawable round(){GradientDrawable d=new GradientDrawable();d.setColor(0x44244356);d.setCornerRadius(dp(12));d.setStroke(dp(1),0x553F9FC0);return d;}
    private static final class Row extends RecyclerView.ViewHolder{final TextView text;Row(View v,TextView t){super(v);text=t;}}
}
