package dev.huskuraft.effortless.screen.pattern.procedural;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import dev.huskuraft.effortless.EffortlessClient;
import dev.huskuraft.effortless.client.pattern.procedural.CandidateQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.Coordinate;
import dev.huskuraft.effortless.client.pattern.procedural.MaskedWeightSource;
import dev.huskuraft.effortless.client.pattern.procedural.MinimumSpacingConstraint;
import dev.huskuraft.effortless.client.pattern.procedural.NeighborDirection;
import dev.huskuraft.effortless.client.pattern.procedural.SpatialField;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralCleanupRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralDirectionalRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralMaskLayer;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralNeighborCountRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralQuotaRule;
import dev.huskuraft.effortless.client.pattern.procedural.config.ProceduralSpacingRule;
import dev.huskuraft.effortless.screen.settings.SettingOptionsList;
import dev.huskuraft.universal.api.gui.AbstractPanelScreen;
import dev.huskuraft.universal.api.gui.button.Button;
import dev.huskuraft.universal.api.gui.text.TextWidget;
import dev.huskuraft.universal.api.platform.Entrance;
import dev.huskuraft.universal.api.text.Text;

final class EffortlessAdvancedRuleEditScreen
        extends EffortlessProceduralScreen {

    private final Consumer<Object> consumer;
    private final Kind kind;
    private Object rule;
    private ReliableEditBox nameField;
    private String nameDraft;

    private EffortlessAdvancedRuleEditScreen(
            Entrance entrance,
            Consumer<Object> consumer,
            Kind kind,
            Object rule
    ) {
        super(
                entrance,
                Text.text(kind.title),
                PANEL_WIDTH_60,
                PANEL_HEIGHT_FULL
        );
        this.consumer = consumer;
        this.kind = kind;
        this.rule = rule;
        this.nameDraft = rule instanceof ProceduralMaskLayer mask
                ? mask.name()
                : "";
        setDraftCommit(this::commitDraft);
    }

    static void editMask(
            Entrance entrance,
            Consumer<ProceduralMaskLayer> consumer,
            ProceduralMaskLayer value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralMaskLayer) result),
                Kind.MASK, value);
    }

    static void editDirectional(
            Entrance entrance,
            Consumer<ProceduralDirectionalRule> consumer,
            ProceduralDirectionalRule value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralDirectionalRule) result),
                Kind.DIRECTIONAL, value);
    }

    static void editSpacing(
            Entrance entrance,
            Consumer<ProceduralSpacingRule> consumer,
            ProceduralSpacingRule value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralSpacingRule) result),
                Kind.SPACING, value);
    }

    static void editNeighborCount(
            Entrance entrance,
            Consumer<ProceduralNeighborCountRule> consumer,
            ProceduralNeighborCountRule value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralNeighborCountRule) result),
                Kind.NEIGHBOR_COUNT, value);
    }

    static void editQuota(
            Entrance entrance,
            Consumer<ProceduralQuotaRule> consumer,
            ProceduralQuotaRule value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralQuotaRule) result),
                Kind.QUOTA, value);
    }

    static void editCleanup(
            Entrance entrance,
            Consumer<ProceduralCleanupRule> consumer,
            ProceduralCleanupRule value
    ) {
        attach(entrance, result -> consumer.accept((ProceduralCleanupRule) result),
                Kind.CLEANUP, value);
    }

    private static void attach(
            Entrance entrance,
            Consumer<Object> consumer,
            Kind kind,
            Object value
    ) {
        new EffortlessAdvancedRuleEditScreen(
                entrance, consumer, kind, value
        ).attach();
    }

    @Override
    protected EffortlessClient getEntrance() {
        return (EffortlessClient) super.getEntrance();
    }

    @Override
    public void onCreate() {
        title();
        int optionsY = getTop() + PANEL_TITLE_HEIGHT_1;
        if (kind == Kind.MASK) {
            addWidget(new TextWidget(
                    getEntrance(), getLeft() + PADDINGS_H, optionsY + 6,
                    Text.text("Layer name")
            ));
            nameField = addWidget(new ReliableEditBox(
                    getEntrance(),
                    getLeft() + PADDINGS_H + 80,
                    optionsY,
                    getWidth() - PADDINGS_H * 2 - 80,
                    20,
                    Text.text("Layer name")
            ));
            nameField.setMaxLength(80);
            nameField.setValue(nameDraft);
            nameField.setChangeListener(value -> nameDraft = value);
            optionsY += 24;
        }

        var options = addWidget(new ProceduralSettingOptionsList(
                getEntrance(),
                getLeft() + PADDINGS_H,
                optionsY,
                getWidth() - PADDINGS_H * 2 - 8,
                getHeight() - (optionsY - getTop()) - PANEL_BUTTON_ROW_HEIGHT_1,
                false,
                false
        ));
        options.setAlwaysShowScrollbar(true);
        switch (kind) {
            case MASK -> createMaskOptions(options);
            case DIRECTIONAL -> createDirectionalOptions(options);
            case SPACING -> createSpacingOptions(options);
            case NEIGHBOR_COUNT -> createNeighborCountOptions(options);
            case QUOTA -> createQuotaOptions(options);
            case CLEANUP -> createCleanupOptions(options);
        }

        addWidget(Button.builder(
                getEntrance(),
                Text.text("Discard"),
                button -> discardAndDetach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0f, 0.5f
        ).build());
        addWidget(Button.builder(
                getEntrance(),
                Text.text("Back"),
                button -> detach()
        ).setBoundsGrid(
                getLeft(), getTop(), getWidth(), getHeight(), 0f, 0.5f, 0.5f
        ).build());
    }

    private boolean commitDraft() {
        if (kind == Kind.MASK) {
            nameField.commitVisibleValue();
            var mask = (ProceduralMaskLayer) rule;
            String name = nameDraft.trim();
            rule = copyMask(
                    mask,
                    name.isEmpty() ? "Layer" : name,
                    mask.enabled(),
                    mask.shape(),
                    mask.coordinate(),
                    mask.minimum(),
                    mask.maximum(),
                    mask.inverted(),
                    mask.itemIds(),
                    mask.mode(),
                    mask.multiplier(),
                    mask.period(),
                    mask.thickness()
            );
        }
        consumer.accept(rule);
        return true;
    }

    private void createMaskOptions(SettingOptionsList options) {
        var mask = (ProceduralMaskLayer) rule;
        options.addSwitchEntry(
                Text.text("Enabled"),
                Text.empty(),
                mask.enabled(),
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), value, current.shape(),
                            current.coordinate(), current.minimum(), current.maximum(),
                            current.inverted(), current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addSelectorEntry(
                Text.text("Shape"),
                Text.empty(),
                labels(MaskedWeightSource.Shape.values()),
                List.of(MaskedWeightSource.Shape.values()),
                mask.shape(),
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(), value,
                            current.coordinate(), current.minimum(), current.maximum(),
                            current.inverted(), current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addSelectorEntry(
                Text.text("Coordinate"),
                Text.empty(),
                labels(Coordinate.values()),
                List.of(Coordinate.values()),
                mask.coordinate(),
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), value, current.minimum(), current.maximum(),
                            current.inverted(), current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        var field = mask.spatialField();
        options.addSelectorEntry(
                Text.text("Field shape"),
                Text.empty(),
                labels(SpatialField.Shape.values()),
                List.of(SpatialField.Shape.values()),
                field.shape(),
                value -> updateMaskField(current -> current.withShape(value))
        );
        options.addNumberEntry(
                Text.text("Field rotation"),
                Text.empty(),
                field.rotationDegrees(),
                -360.0,
                360.0,
                5.0,
                value -> updateMaskField(current ->
                        current.withRotation(value)
                )
        );
        options.addNumberEntry(
                Text.text("Field center X"),
                Text.empty(),
                field.centerX(),
                -4.0,
                4.0,
                0.05,
                value -> updateMaskField(current -> current.withCenter(
                        value, current.centerY(), current.centerZ()
                ))
        );
        options.addNumberEntry(
                Text.text("Field center Y"),
                Text.empty(),
                field.centerY(),
                -4.0,
                4.0,
                0.05,
                value -> updateMaskField(current -> current.withCenter(
                        current.centerX(), value, current.centerZ()
                ))
        );
        options.addNumberEntry(
                Text.text("Field center Z"),
                Text.empty(),
                field.centerZ(),
                -4.0,
                4.0,
                0.05,
                value -> updateMaskField(current -> current.withCenter(
                        current.centerX(), current.centerY(), value
                ))
        );
        options.addNumberEntry(
                Text.text("Field scale X"),
                Text.empty(),
                field.scaleX(),
                0.000001,
                64.0,
                0.05,
                value -> updateMaskField(current -> current.withScale(
                        value, current.scaleY(), current.scaleZ()
                ))
        );
        options.addNumberEntry(
                Text.text("Field scale Y"),
                Text.empty(),
                field.scaleY(),
                0.000001,
                64.0,
                0.05,
                value -> updateMaskField(current -> current.withScale(
                        current.scaleX(), value, current.scaleZ()
                ))
        );
        options.addNumberEntry(
                Text.text("Field scale Z"),
                Text.empty(),
                field.scaleZ(),
                0.000001,
                64.0,
                0.05,
                value -> updateMaskField(current -> current.withScale(
                        current.scaleX(), current.scaleY(), value
                ))
        );
        options.addIntegerEntry(
                Text.text("Field polygon sides"),
                Text.empty(),
                field.polygonSides(),
                3,
                32,
                value -> updateMaskField(current ->
                        current.withPolygonSides(value)
                )
        );
        options.addNumberEntry(
                Text.text("Field curve amount"),
                Text.empty(),
                field.curvature(),
                -4.0,
                4.0,
                0.05,
                value -> updateMaskField(current ->
                        current.withCurvature(value)
                )
        );
        options.addIntegerEntry(
                Text.text("Field repeat"),
                Text.empty(),
                field.repeat(),
                1,
                64,
                value -> updateMaskField(current ->
                        current.withRepeat(value)
                )
        );
        options.addSwitchEntry(
                Text.text("Reverse field"),
                Text.empty(),
                field.inverted(),
                value -> updateMaskField(current ->
                        current.withInverted(value)
                )
        );
        options.addNumberEntry(
                Text.text("Field warp"),
                Text.empty(),
                field.warpAmount(),
                0.0,
                2.0,
                0.05,
                value -> updateMaskField(current -> current.withWarp(
                        value,
                        current.warpFrequency(),
                        current.warpSalt()
                ))
        );
        options.addNumberEntry(
                Text.text("Field warp frequency"),
                Text.empty(),
                field.warpFrequency(),
                0.000001,
                1024.0,
                0.01,
                value -> updateMaskField(current -> current.withWarp(
                        current.warpAmount(),
                        value,
                        current.warpSalt()
                ))
        );
        options.addNumberEntry(
                Text.text("Range minimum"),
                Text.empty(),
                mask.minimum(),
                0.0,
                1.0,
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), value,
                            Math.max(value, current.maximum()),
                            current.inverted(), current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addNumberEntry(
                Text.text("Range maximum"),
                Text.empty(),
                mask.maximum(),
                0.0,
                1.0,
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(),
                            Math.min(current.minimum(), value), value,
                            current.inverted(), current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addSwitchEntry(
                Text.text("Invert mask"),
                Text.empty(),
                mask.inverted(),
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), current.minimum(),
                            current.maximum(), value, current.itemIds(), current.mode(),
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addSelectorEntry(
                Text.text("Layer operation"),
                Text.empty(),
                labels(MaskedWeightSource.Mode.values()),
                List.of(MaskedWeightSource.Mode.values()),
                mask.mode(),
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), current.minimum(),
                            current.maximum(), current.inverted(), current.itemIds(), value,
                            current.multiplier(), current.period(), current.thickness());
                }
        );
        options.addNumberEntry(
                Text.text("Weight multiplier"),
                Text.empty(),
                mask.multiplier(),
                0.000001,
                1_000_000.0,
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), current.minimum(),
                            current.maximum(), current.inverted(), current.itemIds(),
                            current.mode(), value, current.period(), current.thickness());
                }
        );
        options.addIntegerEntry(
                Text.text("Band/checker period"),
                Text.empty(),
                mask.period(),
                1,
                1024,
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), current.minimum(),
                            current.maximum(), current.inverted(), current.itemIds(),
                            current.mode(), current.multiplier(), value, current.thickness());
                }
        );
        options.addIntegerEntry(
                Text.text("Surface thickness"),
                Text.empty(),
                mask.thickness(),
                1,
                1024,
                value -> {
                    var current = (ProceduralMaskLayer) rule;
                    rule = copyMask(current, current.name(), current.enabled(),
                            current.shape(), current.coordinate(), current.minimum(),
                            current.maximum(), current.inverted(), current.itemIds(),
                            current.mode(), current.multiplier(), current.period(), value);
                }
        );
        addItemList(options, "Affected blocks", mask.itemIds(), value -> {
            var current = (ProceduralMaskLayer) rule;
            rule = copyMask(current, current.name(), current.enabled(), current.shape(),
                    current.coordinate(), current.minimum(), current.maximum(),
                    current.inverted(), value, current.mode(), current.multiplier(),
                    current.period(), current.thickness());
        });
    }

    private void createDirectionalOptions(SettingOptionsList options) {
        var value = (ProceduralDirectionalRule) rule;
        addSingleItem(options, "Placed block", value.itemId(), item -> {
            var current = (ProceduralDirectionalRule) rule;
            rule = new ProceduralDirectionalRule(
                    item, current.direction(), current.allowedItemIds(),
                    current.rejectUnresolved()
            );
        });
        options.addSelectorEntry(
                Text.text("Required direction"),
                Text.empty(),
                labels(NeighborDirection.values()),
                List.of(NeighborDirection.values()),
                value.direction(),
                direction -> {
                    var current = (ProceduralDirectionalRule) rule;
                    rule = new ProceduralDirectionalRule(
                            current.itemId(), direction, current.allowedItemIds(),
                            current.rejectUnresolved()
                    );
                }
        );
        options.addSwitchEntry(
                Text.text("Reject unresolved future"),
                Text.empty(),
                value.rejectUnresolved(),
                reject -> {
                    var current = (ProceduralDirectionalRule) rule;
                    rule = new ProceduralDirectionalRule(
                            current.itemId(), current.direction(),
                            current.allowedItemIds(), reject
                    );
                }
        );
        addItemList(options, "Allowed neighbors", value.allowedItemIds(), items -> {
            var current = (ProceduralDirectionalRule) rule;
            rule = new ProceduralDirectionalRule(
                    current.itemId(), current.direction(), items,
                    current.rejectUnresolved()
            );
        });
    }

    private void createSpacingOptions(SettingOptionsList options) {
        var value = (ProceduralSpacingRule) rule;
        options.addIntegerEntry(
                Text.text("Minimum radius"),
                Text.empty(),
                value.radius(),
                1,
                MinimumSpacingConstraint.MAXIMUM_RADIUS,
                radius -> {
                    var current = (ProceduralSpacingRule) rule;
                    rule = new ProceduralSpacingRule(
                            current.itemIds(), radius, current.metric()
                    );
                }
        );
        options.addSelectorEntry(
                Text.text("Distance metric"),
                Text.empty(),
                labels(MinimumSpacingConstraint.DistanceMetric.values()),
                List.of(MinimumSpacingConstraint.DistanceMetric.values()),
                value.metric(),
                metric -> {
                    var current = (ProceduralSpacingRule) rule;
                    rule = new ProceduralSpacingRule(
                            current.itemIds(), current.radius(), metric
                    );
                }
        );
        addItemList(options, "Spaced blocks", value.itemIds(), items -> {
            var current = (ProceduralSpacingRule) rule;
            rule = new ProceduralSpacingRule(
                    items, current.radius(), current.metric()
            );
        });
    }

    private void createNeighborCountOptions(SettingOptionsList options) {
        var value = (ProceduralNeighborCountRule) rule;
        addSingleItem(options, "Placed block", value.itemId(), item -> {
            var current = (ProceduralNeighborCountRule) rule;
            rule = new ProceduralNeighborCountRule(
                    item, current.neighborItemIds(), current.minimum(),
                    current.maximum()
            );
        });
        options.addIntegerEntry(
                Text.text("Minimum matching neighbors"),
                Text.empty(),
                value.minimum(),
                0,
                26,
                minimum -> {
                    var current = (ProceduralNeighborCountRule) rule;
                    rule = new ProceduralNeighborCountRule(
                            current.itemId(), current.neighborItemIds(), minimum,
                            Math.max(minimum, current.maximum())
                    );
                }
        );
        options.addIntegerEntry(
                Text.text("Maximum matching neighbors"),
                Text.empty(),
                value.maximum(),
                0,
                26,
                maximum -> {
                    var current = (ProceduralNeighborCountRule) rule;
                    rule = new ProceduralNeighborCountRule(
                            current.itemId(), current.neighborItemIds(),
                            Math.min(current.minimum(), maximum), maximum
                    );
                }
        );
        addItemList(options, "Counted neighbors", value.neighborItemIds(), items -> {
            var current = (ProceduralNeighborCountRule) rule;
            rule = new ProceduralNeighborCountRule(
                    current.itemId(), items, current.minimum(), current.maximum()
            );
        });
    }

    private void createQuotaOptions(SettingOptionsList options) {
        var value = (ProceduralQuotaRule) rule;
        addSingleItem(options, "Limited block", value.itemId(), item -> {
            var current = (ProceduralQuotaRule) rule;
            rule = new ProceduralQuotaRule(
                    item, current.minimum(), current.maximum(), current.unit()
            );
        });
        options.addSelectorEntry(
                Text.text("Quota unit"),
                Text.empty(),
                labels(CandidateQuotaRule.Unit.values()),
                List.of(CandidateQuotaRule.Unit.values()),
                value.unit(),
                unit -> {
                    var current = (ProceduralQuotaRule) rule;
                    rule = quotaWithUnit(current, unit);
                    recreate();
                }
        );
        double quotaMaximum = value.unit() == CandidateQuotaRule.Unit.FRACTION
                ? 1.0
                : 1_000_000.0;
        double quotaStep = value.unit() == CandidateQuotaRule.Unit.FRACTION
                ? 0.05
                : 1.0;
        options.addNumberEntry(
                Text.text("Minimum (fraction 0..1 or count)"),
                Text.empty(),
                value.minimum(),
                0.0,
                quotaMaximum,
                quotaStep,
                minimum -> {
                    var current = (ProceduralQuotaRule) rule;
                    rule = new ProceduralQuotaRule(
                            current.itemId(), minimum,
                            Math.max(minimum, current.maximum()),
                            current.unit()
                    );
                }
        );
        options.addNumberEntry(
                Text.text("Maximum (fraction 0..1 or count)"),
                Text.empty(),
                value.maximum(),
                0.0,
                quotaMaximum,
                quotaStep,
                maximum -> {
                    var current = (ProceduralQuotaRule) rule;
                    rule = new ProceduralQuotaRule(
                            current.itemId(),
                            Math.min(current.minimum(), maximum), maximum,
                            current.unit()
                    );
                }
        );
    }

    private void createCleanupOptions(SettingOptionsList options) {
        var value = (ProceduralCleanupRule) rule;
        addItemList(options, "Replace these blocks", value.sourceItemIds(), items -> {
            var current = (ProceduralCleanupRule) rule;
            rule = new ProceduralCleanupRule(
                    items, current.matchingNeighborItemIds(),
                    current.replacementItemId(), current.minimumMatches(),
                    current.maximumMatches()
            );
        });
        addItemList(
                options,
                "Matching neighbor blocks",
                value.matchingNeighborItemIds(),
                items -> {
                    var current = (ProceduralCleanupRule) rule;
                    rule = new ProceduralCleanupRule(
                            current.sourceItemIds(), items,
                            current.replacementItemId(), current.minimumMatches(),
                            current.maximumMatches()
                    );
                }
        );
        addSingleItem(options, "Replacement block", value.replacementItemId(), item -> {
            var current = (ProceduralCleanupRule) rule;
            rule = new ProceduralCleanupRule(
                    current.sourceItemIds(), current.matchingNeighborItemIds(),
                    item, current.minimumMatches(), current.maximumMatches()
            );
        });
        options.addIntegerEntry(
                Text.text("Minimum matching neighbors"),
                Text.empty(),
                value.minimumMatches(),
                0,
                26,
                minimum -> {
                    var current = (ProceduralCleanupRule) rule;
                    rule = new ProceduralCleanupRule(
                            current.sourceItemIds(),
                            current.matchingNeighborItemIds(),
                            current.replacementItemId(), minimum,
                            Math.max(minimum, current.maximumMatches())
                    );
                }
        );
        options.addIntegerEntry(
                Text.text("Maximum matching neighbors"),
                Text.empty(),
                value.maximumMatches(),
                0,
                26,
                maximum -> {
                    var current = (ProceduralCleanupRule) rule;
                    rule = new ProceduralCleanupRule(
                            current.sourceItemIds(),
                            current.matchingNeighborItemIds(),
                            current.replacementItemId(),
                            Math.min(current.minimumMatches(), maximum), maximum
                    );
                }
        );
    }

    private void addSingleItem(
            SettingOptionsList options,
            String title,
            String itemId,
            Consumer<String> consumer
    ) {
        options.addTab(
                Text.text(title),
                Text.empty(),
                itemId,
                consumer,
                (entry, value) -> {
                    entry.getButton().setMessage(Text.text(shortId(value)));
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessCandidatePickerScreen(
                                    getEntrance(),
                                    title,
                                    entry::setItem
                            ).attach()
                    );
                }
        );
    }

    private void addItemList(
            SettingOptionsList options,
            String title,
            List<String> itemIds,
            Consumer<List<String>> consumer
    ) {
        options.addTab(
                Text.text(title),
                Text.empty(),
                itemIds,
                consumer,
                (entry, value) -> {
                    entry.getButton().setMessage(
                            Text.text(value.size() + " blocks")
                    );
                    entry.getButton().setOnPressListener(button ->
                            new EffortlessAllowedItemsScreen(
                                    getEntrance(),
                                    title,
                                    entry::setItem,
                                    value
                            ).attach()
                    );
                }
        );
    }

    private void title() {
        addWidget(new TextWidget(
                getEntrance(),
                getLeft() + getWidth() / 2,
                getTop() + PANEL_TITLE_HEIGHT_1 - 10,
                workbenchTitle(),
                TextWidget.Gravity.CENTER
        ));
    }

    private static List<Text> labels(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(value -> Text.text(
                        value.name().toLowerCase().replace('_', ' ')
                ))
                .toList();
    }

    private static String shortId(String value) {
        int separator = value.indexOf(':');
        return separator >= 0 ? value.substring(separator + 1) : value;
    }

    private static ProceduralMaskLayer copyMask(
            ProceduralMaskLayer current,
            String name,
            boolean enabled,
            MaskedWeightSource.Shape shape,
            Coordinate coordinate,
            double minimum,
            double maximum,
            boolean inverted,
            List<String> items,
            MaskedWeightSource.Mode mode,
            double multiplier,
            int period,
            int thickness
    ) {
        var field = current.spatialField();
        if (field.coordinate() != coordinate) {
            field = field.withCoordinate(coordinate);
        }
        return new ProceduralMaskLayer(
                current.id(), name, enabled, shape, coordinate, minimum, maximum,
                inverted, items, mode, multiplier, period, thickness, field
        );
    }

    private void updateMaskField(
            java.util.function.UnaryOperator<SpatialField> updater
    ) {
        var current = (ProceduralMaskLayer) rule;
        var changed = updater.apply(current.spatialField());
        rule = new ProceduralMaskLayer(
                current.id(),
                current.name(),
                current.enabled(),
                current.shape(),
                changed.coordinate(),
                current.minimum(),
                current.maximum(),
                current.inverted(),
                current.itemIds(),
                current.mode(),
                current.multiplier(),
                current.period(),
                current.thickness(),
                changed
        );
    }

    private static ProceduralQuotaRule quotaWithUnit(
            ProceduralQuotaRule current,
            CandidateQuotaRule.Unit unit
    ) {
        if (unit == CandidateQuotaRule.Unit.FRACTION) {
            double minimum = Math.clamp(current.minimum(), 0.0, 1.0);
            double maximum = Math.clamp(current.maximum(), minimum, 1.0);
            return new ProceduralQuotaRule(
                    current.itemId(), minimum, maximum, unit
            );
        }
        int minimum = Math.max(0, (int) Math.ceil(current.minimum()));
        int maximum = Math.max(
                minimum,
                (int) Math.floor(current.maximum())
        );
        return new ProceduralQuotaRule(
                current.itemId(), minimum, maximum, unit
        );
    }

    private enum Kind {
        MASK("Edit named mask layer"),
        DIRECTIONAL("Edit directional rule"),
        SPACING("Edit minimum spacing"),
        NEIGHBOR_COUNT("Edit neighbor-count rule"),
        QUOTA("Edit block quota"),
        CLEANUP("Edit cleanup rule");

        private final String title;

        Kind(String title) {
            this.title = title;
        }
    }
}
