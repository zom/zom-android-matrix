package info.guardianproject.keanuapp.ui.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import info.guardianproject.keanuapp.R;
import info.guardianproject.keanuapp.ui.conversation.QuickReaction;

public class QuickReactionsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface QuickReactionsRecyclerViewAdapterListener {
        void onReactionClicked(QuickReaction reaction);
    }

    private final Context context;
    private List<QuickReaction> reactions;
    private QuickReactionsRecyclerViewAdapterListener listener;

    public QuickReactionsRecyclerViewAdapter(Context context, List<QuickReaction> reactions) {
        super();
        setHasStableIds(true);
        this.context = context;
        this.reactions = reactions;
    }

    public void setListener(QuickReactionsRecyclerViewAdapterListener listener) {
        this.listener = listener;
    }

    private Context getContext() {
        return context;
    }

    @Override
    public int getItemCount() {
        return reactions.size();
    }

    @Override
    public long getItemId(int position) {
        return reactions.get(position).hashCode();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.quick_reaction, parent, false);
        return new QuickReactionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
        QuickReactionViewHolder viewHolder = (QuickReactionViewHolder) holder;
        QuickReaction quickReaction = reactions.get(position);
        viewHolder.bindModel(quickReaction);
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) {
                    listener.onReactionClicked(quickReaction);
                }
            }
        });
    }

    private class QuickReactionViewHolder extends RecyclerView.ViewHolder {
        private final TextView emoji;
        private final TextView count;

        QuickReactionViewHolder(View view) {
            super(view);
            emoji = view.findViewById(R.id.emoji);
            count = view.findViewById(R.id.count);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + emoji.getText() + "'";
        }

        void bindModel(final QuickReaction reaction) {
            emoji.setText(reaction.reaction);
            count.setText(String.valueOf(reaction.senders.size()));
            itemView.setBackground(reaction.sentByMe ? selectedBackgroundDrawable : null);
        }

    }

    private static Drawable selectedBackgroundDrawable = null;
    private static int selectedBackgroundDrawableColor = -1;

    public static void setThemeColor(Context context, int themeColor) {
        try {
            // Only update drawable if color has changed
            if (themeColor != selectedBackgroundDrawableColor) {
                selectedBackgroundDrawableColor = themeColor;
                GradientDrawable d = new GradientDrawable();

                // Set stroke to 7f alpha and background to 20 alpha. Color is the theme color.
                d.setColor(0x20000000 | (0x00ffffff & themeColor));
                d.setStroke(dpToPx(1, context), 0x7f000000 | (0x00ffffff & themeColor));
                d.setCornerRadius(dpToPx(8, context));
                selectedBackgroundDrawable = d;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static int dpToPx(int dp, Context ctx)
    {
        Resources r = ctx.getResources();
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, r.getDisplayMetrics());
    }
}
