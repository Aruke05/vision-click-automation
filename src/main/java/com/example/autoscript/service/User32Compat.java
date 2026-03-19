package com.example.autoscript.service;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

public interface User32Compat extends StdCallLibrary {
    User32Compat INSTANCE = Native.load("user32", User32Compat.class, W32APIOptions.DEFAULT_OPTIONS);

    boolean EnumWindows(WinUser.WNDENUMPROC lpEnumFunc, Pointer data);
    boolean IsWindowVisible(HWND hWnd);
    int GetWindowText(HWND hWnd, char[] lpString, int nMaxCount);
    int GetClassName(HWND hWnd, char[] lpClassName, int nMaxCount);
    boolean GetWindowRect(HWND hWnd, RECT rect);
    int GetWindowThreadProcessId(HWND hWnd, IntByReference lpdwProcessId);
    boolean IsWindow(HWND hWnd);
    boolean IsIconic(HWND hWnd);
    boolean ShowWindow(HWND hWnd, int nCmdShow);
    boolean SetForegroundWindow(HWND hWnd);
    HWND GetForegroundWindow();
    boolean GetClientRect(HWND hWnd, RECT rect);
    boolean PostMessage(HWND hWnd, int msg, WPARAM wParam, LPARAM lParam);
    boolean RegisterHotKey(HWND hWnd, int id, int fsModifiers, int vk);
    boolean UnregisterHotKey(HWND hWnd, int id);
    int GetMessage(WinUser.MSG lpMsg, HWND hWnd, int wMsgFilterMin, int wMsgFilterMax);
    boolean TranslateMessage(WinUser.MSG lpMsg);
    LRESULT DispatchMessage(WinUser.MSG lpMsg);
    boolean PostThreadMessage(int idThread, int msg, WPARAM wParam, LPARAM lParam);
    boolean ClientToScreen(HWND hWnd, POINT lpPoint);
}
