// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.DeleteReviewer.Input;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.BatchUpdate.ChangeContext;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.List;

@Singleton
public class DeleteReviewer implements RestModifyView<ReviewerResource, Input> {
  public static class Input {
  }

  private final Provider<ReviewDb> dbProvider;
  private final ApprovalsUtil approvalsUtil;
  private final ChangeMessagesUtil cmUtil;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final IdentifiedUser.GenericFactory userFactory;

  @Inject
  DeleteReviewer(Provider<ReviewDb> dbProvider,
      ApprovalsUtil approvalsUtil,
      ChangeMessagesUtil cmUtil,
      BatchUpdate.Factory batchUpdateFactory,
      IdentifiedUser.GenericFactory userFactory) {
    this.dbProvider = dbProvider;
    this.approvalsUtil = approvalsUtil;
    this.cmUtil = cmUtil;
    this.batchUpdateFactory = batchUpdateFactory;
    this.userFactory = userFactory;
  }

  @Override
  public Response<?> apply(ReviewerResource rsrc, Input input)
      throws RestApiException, UpdateException {
    try (BatchUpdate bu = batchUpdateFactory.create(dbProvider.get(),
        rsrc.getChangeResource().getProject(),
        rsrc.getChangeResource().getUser(), TimeUtil.nowTs())) {
      Op op = new Op(rsrc.getReviewerUser().getAccountId());
      bu.addOp(rsrc.getChange().getId(), op);
      bu.execute();
    }

    return Response.none();
  }

  private class Op extends BatchUpdate.Op {
    private final Account.Id reviewerId;

    Op(Account.Id reviewerId) {
      this.reviewerId = reviewerId;
    }

    @Override
    public boolean updateChange(ChangeContext ctx)
        throws AuthException, ResourceNotFoundException, OrmException {
      PatchSet.Id currPs = ctx.getChange().currentPatchSetId();
      StringBuilder msg = new StringBuilder();
      List<PatchSetApproval> del = Lists.newArrayList();
      for (PatchSetApproval a : approvals(ctx, reviewerId)) {
        if (ctx.getControl().canRemoveReviewer(a)) {
          del.add(a);
          if (a.getPatchSetId().equals(currPs)
              && a.getValue() != 0) {
            if (msg.length() == 0) {
              msg.append("Removed the following votes:\n\n");
            }
            msg.append("* ")
                .append(a.getLabel()).append(formatLabelValue(a.getValue()))
                .append(" by ").append(userFactory.create(a.getAccountId()).getNameEmail())
                .append("\n");
          }
        } else {
          throw new AuthException("delete not permitted");
        }
      }
      if (del.isEmpty()) {
        throw new ResourceNotFoundException();
      }
      ctx.getDb().patchSetApprovals().delete(del);
      ChangeUpdate update = ctx.getUpdate(currPs);
      update.removeReviewer(reviewerId);

      if (msg.length() > 0) {
        ChangeMessage changeMessage =
            new ChangeMessage(new ChangeMessage.Key(ctx.getChange().getId(),
                ChangeUtil.messageUUID(ctx.getDb())),
                ctx.getUser().getAccountId(),
                TimeUtil.nowTs(), currPs);
        changeMessage.setMessage(msg.toString());
        cmUtil.addChangeMessage(ctx.getDb(), update, changeMessage);
      }
      return true;
    }

    private Iterable<PatchSetApproval> approvals(ChangeContext ctx,
        final Account.Id accountId) throws OrmException {
      return Iterables.filter(
          approvalsUtil.byChange(ctx.getDb(), ctx.getNotes()).values(),
          new Predicate<PatchSetApproval>() {
            @Override
            public boolean apply(PatchSetApproval input) {
              return accountId.equals(input.getAccountId());
            }
          });
    }

    private String formatLabelValue(short value) {
      if (value > 0) {
        return "+" + value;
      } else {
        return Short.toString(value);
      }
    }
  }
}
