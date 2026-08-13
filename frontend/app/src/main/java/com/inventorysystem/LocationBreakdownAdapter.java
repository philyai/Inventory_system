package com.inventorysystem;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.inventorysystem.Model.LocationBreakdownModel;
import com.inventorysystem.Model.ItemModel;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LocationBreakdownAdapter extends RecyclerView.Adapter<LocationBreakdownAdapter.ViewHolder> {

    private List<LocationBreakdownModel> items = new ArrayList<>();
    private final Set<Integer> expandedPositions = new HashSet<>();

    public void submitList(List<LocationBreakdownModel> newItems) {
        this.items = newItems != null ? newItems : new ArrayList<>();
        expandedPositions.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    @SuppressLint("ResourceType")
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.xml.item_location_breakdown, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LocationBreakdownModel item = items.get(position);

        holder.name.setText(item.getLocationName());
        String description = item.getDescription();
        holder.description.setText(description != null && !description.isEmpty()
                ? description : "No description");
        holder.itemCount.setText(item.getTotalQuantity() + " units");
        holder.units.setText(item.getTotalQuantity() + " total units");
        holder.value.setText(String.format(Locale.US, "\u20b1%,.2f total value",
                item.getTotalValue()));
        populateItems(holder, item);

        boolean expanded = expandedPositions.contains(position);
        holder.detail.setVisibility(expanded ? View.VISIBLE : View.GONE);
        holder.chevron.setRotation(expanded ? 180f : 0f);

        holder.header.setOnClickListener(v -> {
            boolean nowExpanded = !expandedPositions.contains(position);
            if (nowExpanded) {
                expandedPositions.add(position);
            } else {
                expandedPositions.remove(position);
            }
            notifyItemChanged(position);
        });
    }

    private void populateItems(ViewHolder holder, LocationBreakdownModel location) {
        holder.itemsContainer.removeAllViews();

        Map<String, List<ItemModel>> itemsByCategory = new LinkedHashMap<>();
        for (ItemModel item : location.getItems()) {
            String category = item.getCategory() != null
                    && item.getCategory().getCategoryName() != null
                    ? item.getCategory().getCategoryName()
                    : "Other";
            itemsByCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(item);
        }

        if (itemsByCategory.isEmpty()) {
            TextView empty = createLine(holder, "No item details available", 12f, false);
            empty.setTextColor(holder.itemView.getResources().getColor(R.color.gray_blue));
            holder.itemsContainer.addView(empty);
            return;
        }

        for (Map.Entry<String, List<ItemModel>> group : itemsByCategory.entrySet()) {
            TextView category = createLine(holder, group.getKey(), 12.5f, true);
            category.setPadding(0, 10, 0, 4);
            holder.itemsContainer.addView(category);

            for (ItemModel item : group.getValue()) {
                String code = item.getItemCode() != null && !item.getItemCode().isEmpty()
                        ? " (" + item.getItemCode() + ")" : "";
                String line = item.getItemName() + code + " \u2014 "
                        + item.getQuantity() + " units";
                holder.itemsContainer.addView(createLine(holder, line, 12f, false));
            }
        }
    }

    private TextView createLine(ViewHolder holder, String text, float size, boolean bold) {
        TextView view = new TextView(holder.itemView.getContext());
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(holder.itemView.getResources().getColor(R.color.dark_gray));
        view.setPadding(0, 4, 0, 4);
        if (bold) {
            view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return view;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout header;
        TextView name, description, itemCount, chevron, units, value;
        LinearLayout detail, itemsContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            header = itemView.findViewById(R.id.locHeader);
            name = itemView.findViewById(R.id.locName);
            description = itemView.findViewById(R.id.locDescription);
            itemCount = itemView.findViewById(R.id.locItemCount);
            chevron = itemView.findViewById(R.id.locChevron);
            detail = itemView.findViewById(R.id.locDetail);
            units = itemView.findViewById(R.id.locUnits);
            value = itemView.findViewById(R.id.locValue);
            itemsContainer = itemView.findViewById(R.id.locItemsContainer);
        }
    }
}
