package com.example.autoscript.gui;

import com.example.autoscript.model.WindowInfo;

import javax.swing.table.AbstractTableModel;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class WindowTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {
            "PID", "标题", "类名", "进程", "WindowRect", "ClientRect"
    };

    private final List<WindowInfo> windows = new ArrayList<>();

    public void setWindows(List<WindowInfo> values) {
        windows.clear();
        if (values != null) {
            windows.addAll(values);
        }
        fireTableDataChanged();
    }

    public WindowInfo getWindowAt(int row) {
        if (row < 0 || row >= windows.size()) {
            return null;
        }
        return windows.get(row);
    }

    @Override
    public int getRowCount() {
        return windows.size();
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
        WindowInfo w = windows.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> w.getProcessId();
            case 1 -> w.getTitle();
            case 2 -> w.getClassName();
            case 3 -> w.getProcessName();
            case 4 -> rectToText(w.getWindowRect());
            case 5 -> rectToText(w.getClientRectOnScreen());
            default -> "";
        };
    }

    private String rectToText(Rectangle rect) {
        if (rect == null) {
            return "";
        }
        return String.format("(%d,%d,%d,%d)", rect.x, rect.y, rect.width, rect.height);
    }
}
