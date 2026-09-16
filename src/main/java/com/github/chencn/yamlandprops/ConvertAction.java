package com.github.chencn.yamlandprops;

import com.github.chencn.yamlandprops.consts.Constant;
import com.github.chencn.yamlandprops.consts.MsgConsts;
import com.github.chencn.yamlandprops.props2yaml.Props2Yaml;
import com.github.chencn.yamlandprops.utils.CommonUtils;
import com.github.chencn.yamlandprops.yaml2props.Yaml2Props;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author xqchen
 */
public class ConvertAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * 通过 NotificationGroupManager 发送通知，替代已过时的 new Notification(...) 构造方式。
     *
     * @param project 当前项目，可为 null（应用级通知）
     * @param title   通知标题
     * @param content 通知内容
     * @param type    通知类型
     */
    private void notify(Project project, String title, String content, NotificationType type) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(Constant.GROUP_DISPLAY_ID)
                .createNotification(title, content, type)
                .notify(project);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        super.update(event);
        Presentation presentation = event.getPresentation();
        // get file type
        final String fileType = CommonUtils.getFileType(event, false);
        // 根据类型动态控制Action的隐藏显示
        if(!StringUtil.isEmpty(fileType) && presentation.isEnabled()) {
            event.getPresentation()
                    .setEnabledAndVisible(Constant.YAML.equals(fileType) || Constant.PROPERTIES.equals(fileType));
        } else {
            event.getPresentation()
                    .setEnabledAndVisible(false);
        }

    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent event) {
        // get file type
        final String fileType = CommonUtils.getFileType(event, true);
        final PsiFile selectedFile = CommonUtils.getSelectedFile(event, true);
        if(StringUtil.isEmpty(fileType) || null == selectedFile) {
            return;
        }
        final VirtualFile file = selectedFile.getVirtualFile();
        if(null == file) {
            notify(event.getProject(), MsgConsts.NO_FILE_SELECTED, MsgConsts.SELECT_FILE_FIRST, NotificationType.ERROR);
            return;
        }
        ApplicationManager.getApplication()
                .runWriteAction(() -> {
                    try {
                        String content = new String(file.contentsToByteArray());
                        if(StringUtil.isEmpty(content)) {
                            notify(event.getProject(), MsgConsts.FILE_NOT_EMPTY, MsgConsts.SELECT_FILE_FIRST, NotificationType.ERROR);
                            return;
                        }
                        // YAML文件处理
                        if(Constant.YAML.equals(fileType)) {
                            String yamlContent = Yaml2Props.fromContent(content)
                                    .convert();
                            file.rename(this, file.getNameWithoutExtension() + Constant.PROPERTIES_SUFFIX);
                            file.setBinaryContent(yamlContent.getBytes());

                            notify(event.getProject(), MsgConsts.SUCCESS, MsgConsts.YAML2PROPS, NotificationType.INFORMATION);
                        }// PROPERTIES文件处理
                        else if(Constant.PROPERTIES.equals(fileType)) {
                            String propsContent = Props2Yaml.fromContent(content)
                                    .convert();
                            file.rename(this, file.getNameWithoutExtension() + Constant.YAML_SUFFIX);
                            file.setBinaryContent(propsContent.getBytes());
                            notify(event.getProject(), MsgConsts.SUCCESS, MsgConsts.PROPS2YAML, NotificationType.INFORMATION);
                        } else {
                            notify(event.getProject(), MsgConsts.INCORRECT_FILE_SELECTED, MsgConsts.SELECT_PROPS_OR_YAML_FIRST, NotificationType.ERROR);
                        }

                    } catch(ProcessCanceledException e) {
                        // 忽略协程取消异常，这是正常的控制流异常
                        notify(event.getProject(), "Operation cancelled", "The operation was cancelled", NotificationType.INFORMATION);
                    } catch(IOException e) {
                        notify(event.getProject(), MsgConsts.CANNOT_RENAME_FILE, e.getMessage(), NotificationType.ERROR);
                    } catch(Exception e) {
                        notify(event.getProject(), "Unexpected error", "An unexpected error occurred: " + e.getMessage(), NotificationType.ERROR);
                    }
                });
    }

}
