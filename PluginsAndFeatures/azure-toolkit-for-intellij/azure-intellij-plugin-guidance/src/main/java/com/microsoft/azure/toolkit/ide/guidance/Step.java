/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.ide.guidance;

import com.microsoft.azure.toolkit.ide.guidance.config.StepConfig;
import com.microsoft.azure.toolkit.ide.guidance.input.GuidanceInput;
import com.microsoft.azure.toolkit.ide.guidance.input.InputManager;
import com.microsoft.azure.toolkit.ide.guidance.task.TaskManager;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.messager.IAzureMessager;
import com.microsoft.azure.toolkit.lib.common.operation.OperationContext;
import com.microsoft.azure.toolkit.lib.common.task.AzureTask;
import com.microsoft.azure.toolkit.lib.common.task.AzureTaskManager;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import rx.Observable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Getter
@Setter
@RequiredArgsConstructor
public class Step {
    @Nonnull
    private final String id;
    @Nonnull
    private final String title;

    @Nullable
    private final String description;

    @Nonnull
    private final Task task;

    @Nonnull
    private final List<? extends GuidanceInput<?>> inputs;

    @Nonnull
    @ToString.Exclude
    private final Phase phase;

    @Nonnull
    private Status status = Status.INITIAL;
    private IAzureMessager output;
    private List<Consumer<Status>> listenerList = new CopyOnWriteArrayList<>();

    public Step(@Nonnull final StepConfig config, @Nonnull Phase phase) {
        this.phase = phase;
        this.id = UUID.randomUUID().toString();
        this.title = config.getTitle();
        this.description = config.getDescription();
        this.task = TaskManager.createTask(config.getTask(), phase.getCourse().getContext());
        this.inputs = Optional.ofNullable(config.getInputs())
            .map(configs -> configs.stream().map(inputConfig ->
                InputManager.createInputComponent(inputConfig, phase.getCourse().getContext())).collect(Collectors.toList()))
            .orElse(Collections.emptyList());
    }

    public void setStatus(final Status status) {
        this.status = status;
        this.listenerList.forEach(listener -> AzureTaskManager.getInstance().runOnPooledThread(() -> listener.accept(status)));
    }

    public void execute() {
        AzureTaskManager.getInstance().runInBackground(new AzureTask<>(AzureString.format("run task '%s'", this.getTitle()), () -> {
            final IAzureMessager currentMessager = AzureMessager.getMessager();
            OperationContext.current().setMessager(output);
            try {
                setStatus(Status.RUNNING);
                if (this.phase.validateInputs()) {
                    this.applyInputs();
                    Mono.fromCallable(() -> {
                        this.task.execute();
                        return null;
                    }).subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofMinutes(10)).block();
                    setStatus(Status.SUCCEED);
                } else {
                    setStatus(Status.FAILED);
                }
            } catch (final Exception e) {
                setStatus(Status.FAILED);
                AzureMessager.getMessager().error(e);
                AzureMessager.getDefaultMessager().error(e);
            } finally {
                OperationContext.current().setMessager(currentMessager);
            }
        }));
    }

    public String getRenderedDescription() {
        return getContext().render(this.getDescription());
    }

    public String getRenderedTitle() {
        return getContext().render(this.getTitle());
    }

    public Context getContext() {
        return this.getPhase().getContext();
    }

    public void addStatusListener(Consumer<Status> listener) {
        listenerList.add(listener);
    }

    public void removeStatusListener(Consumer<Status> listener) {
        listenerList.remove(listener);
    }

    public void prepare() {
        task.prepare();
        this.setStatus(task.isDone() ? Status.SUCCEED : Status.READY);
    }

    private void applyInputs() {
        this.phase.getInputs().forEach(GuidanceInput::applyResult);
    }
}
