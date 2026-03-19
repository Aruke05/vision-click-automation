package com.example.autoscript.gui;

import com.example.autoscript.model.ConditionConfig;
import com.example.autoscript.model.MonitorRegion;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class ConditionTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "序号", "名称", "相似度(%)", "区域", "点击", "模板数", "表达式"
    };

    private final List<ConditionConfig> conditions = new ArrayList<>();

    public void setConditions(List<ConditionConfig> values) {
        conditions.clear();
        if (values != null) {
            for (ConditionConfig value : values) {
                if (value != null) {
                    conditions.add(new ConditionConfig(value));
                }
            }
        }
        fireTableDataChanged();
    }

    public List<ConditionConfig> getConditions() {
        List<ConditionConfig> copied = new ArrayList<>();
        for (ConditionConfig condition : conditions) {
            copied.add(new ConditionConfig(condition));
        }
        return copied;
    }

    public int addCondition(ConditionConfig condition) {
        ConditionConfig toAdd = condition == null ? new ConditionConfig() : new ConditionConfig(condition);
        conditions.add(toAdd);
        int index = conditions.size() - 1;
        fireTableRowsInserted(index, index);
        return index;
    }

    public void updateCondition(int index, ConditionConfig condition) {
        if (index < 0 || index >= conditions.size() || condition == null) {
            return;
        }
        conditions.set(index, new ConditionConfig(condition));
        fireTableRowsUpdated(index, index);
    }

    public ConditionConfig getConditionAt(int row) {
        if (row < 0 || row >= conditions.size()) {
            return null;
        }
        return new ConditionConfig(conditions.get(row));
    }

    public void removeCondition(int index) {
        if (index < 0 || index >= conditions.size()) {
            return;
        }
        conditions.remove(index);
        fireTableRowsDeleted(index, index);
        if (index < conditions.size()) {
            fireTableRowsUpdated(index, conditions.size() - 1);
        }
    }

    public int moveCondition(int index, int offset) {
        int target = index + offset;
        if (index < 0 || index >= conditions.size() || target < 0 || target >= conditions.size()) {
            return index;
        }
        ConditionConfig src = conditions.get(index);
        conditions.set(index, conditions.get(target));
        conditions.set(target, src);
        fireTableRowsUpdated(Math.min(index, target), Math.max(index, target));
        return target;
    }

    public int getConditionCount() {
        return conditions.size();
    }

    @Override
    public int getRowCount() {
        return conditions.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        ConditionConfig condition = conditions.get(rowIndex);
        MonitorRegion region = condition.getMonitorRegion();
        return switch (columnIndex) {
            case 0 -> rowIndex + 1;
            case 1 -> condition.getName().isBlank() ? "条件" + (rowIndex + 1) : condition.getName();
            case 2 -> (int) Math.round(condition.getThreshold() * 100.0D);
            case 3 -> region == null
                    ? ""
                    : String.format("x=%d,y=%d,w=%d,h=%d",
                    region.getX(), region.getY(), region.getWidth(), region.getHeight());
            case 4 -> String.format("x=%d,y=%d", condition.getClickX(), condition.getClickY());
            case 5 -> condition.getTemplatePaths().size();
            case 6 -> condition.getConditionExpression();
            default -> "";
        };
    }
}
