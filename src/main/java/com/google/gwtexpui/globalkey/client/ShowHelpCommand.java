// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gwtexpui.globalkey.client;

import com.google.gwt.event.dom.client.KeyPressEvent;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.PopupPanel.PositionCallback;


public class ShowHelpCommand extends KeyCommand {
  public ShowHelpCommand() {
    super(0, '?', Util.C.showHelp());
  }

  @Override
  public void onKeyPress(final KeyPressEvent event) {
    final KeyHelpPopup help = new KeyHelpPopup();
    help.setPopupPositionAndShow(new PositionCallback() {
      @Override
      public void setPosition(final int pWidth, final int pHeight) {
        final int left = (Window.getClientWidth() - pWidth) >> 1;
        final int wLeft = Window.getScrollLeft();
        final int wTop = Window.getScrollTop();
        help.setPopupPosition(wLeft + left, wTop + 50);
      }
    });
  }
}
