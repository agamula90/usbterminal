package com.proggroup.areasquarecalculator.api;

import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

public interface LibraryContentAttachable extends ReportAttachable, OnProgressDismissable{

	FragmentManager getSupportFragmentManager();

	int getFragmentContainerId();

	DrawerLayout getDrawerLayout();

	int getToolbarId();

	int getLeftDrawerFragmentId();

	int getFolderDrawable();

	int getFileDrawable();

	FrameLayout getFrameLayout();

	LinearLayout graphContainer();

    void onGraphAttached();

    void onGraphDetached();

	String toolbarTitle();
}
