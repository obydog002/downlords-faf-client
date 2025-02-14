package com.faforever.client.leaderboard;

import com.faforever.client.domain.api.Division;
import com.faforever.client.domain.api.LeagueEntry;
import com.faforever.client.domain.api.Subdivision;
import com.faforever.client.domain.server.PlayerInfo;
import com.faforever.client.fx.NodeController;
import com.faforever.client.fx.ToStringOnlyConverter;
import com.faforever.client.i18n.I18n;
import com.faforever.client.player.PlayerService;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Label;
import javafx.scene.layout.AnchorPane;
import javafx.scene.paint.Color;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SequencedCollection;
import java.util.stream.Collectors;


@Slf4j
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@RequiredArgsConstructor
public class LeaderboardDistributionController extends NodeController<AnchorPane> {

  private static final PseudoClass NOTIFICATION_HIGHLIGHTED_PSEUDO_CLASS = PseudoClass.getPseudoClass(
      "highlighted-bar");

  private final I18n i18n;
  private final PlayerService playerService;

  public AnchorPane distributionRoot;
  public BarChart<String, Integer> ratingDistributionChart;
  public CategoryAxis xAxis;
  public NumberAxis yAxis;

  private final ObjectProperty<List<LeagueEntry>> leagueEntries = new SimpleObjectProperty<>(List.of());
  private final ObjectProperty<List<Subdivision>> subdivisions = new SimpleObjectProperty<>(List.of());
  private final Map<Subdivision, Data<String, Integer>> subdivisionData = new HashMap<>();

  @Override
  protected void onInitialize() {
    yAxis.setTickLabelFormatter(new ToStringOnlyConverter<>(number -> String.valueOf(number.intValue())));
    yAxis.setTickUnit(10d);
    xAxis.labelProperty()
         .bind(leagueEntries.map(Collection::size).map(size -> i18n.get("leaderboard.totalPlayers", size)));

    leagueEntries.when(showing).subscribe(leagueEntries -> {
      updateHighlightedSubdivision();
      updateChartData(leagueEntries);
    });
    subdivisions.when(showing).subscribe(this::updateSubdivisions);
    playerService.currentPlayerProperty().when(showing).subscribe(this::updateHighlightedSubdivision);
  }

  @Override
  public AnchorPane getRoot() {
    return distributionRoot;
  }

  private void updateChartData(List<LeagueEntry> leagueEntries) {
    Map<Subdivision, Long> dataCountMap = leagueEntries.stream().collect(Collectors.groupingBy(LeagueEntry::subdivision,
                                                                                          Collectors.counting()));
    subdivisionData.forEach(
        (subdivision, data) -> data.setYValue(dataCountMap.getOrDefault(subdivision, 0L).intValue()));
  }

  private void updateSubdivisions(List<Subdivision> subdivisions) {
    subdivisionData.clear();
    subdivisions.forEach(subdivision -> subdivisionData.put(subdivision, createSubdivisionData(subdivision)));

    List<String> categories = subdivisions.stream()
                                          .map(Subdivision::division)
                                          .distinct()
                                          .sorted(Comparator.comparing(Division::index))
                                          .map(Division::nameKey)
                                          .map(nameKey -> i18n.get("leagues.divisionName.%s".formatted(nameKey)))
                                          .toList();
    xAxis.setCategories(FXCollections.observableList(categories));

    SequencedCollection<Series<String, Integer>> series = subdivisionData.entrySet()
                                                                         .stream()
                                                                         .sorted(Entry.comparingByKey(
                                                                             Comparator.comparing(Subdivision::division,
                                                                                           Comparator.comparing(
                                                                                               Division::index))
                                                                                       .thenComparing(
                                                                                           Subdivision::index)))
                                                                         .collect(Collectors.groupingBy(
                                                                             entry -> entry.getKey().index(),
                                                                             LinkedHashMap::new,
                                                                             Collectors.mapping(Entry::getValue,
                                                                                                Collectors.collectingAndThen(
                                                                                                    Collectors.toCollection(
                                                                                                        FXCollections::observableArrayList),
                                                                                                    Series::new))))
                                                                         .sequencedValues();

    updateHighlightedSubdivision();

    ratingDistributionChart.setAnimated(false);
    ratingDistributionChart.setData(FXCollections.observableArrayList(series));
    updateChartData(leagueEntries.getValue());
  }

  private void updateHighlightedSubdivision() {
    PlayerInfo currentPlayer = playerService.getCurrentPlayer();
    Subdivision currentPlayerSubdivision = leagueEntries.getValue()
                                                        .stream()
                                                        .filter(leagueEntry -> leagueEntry.player()
                                                                                              .equals(currentPlayer))
                                                        .map(LeagueEntry::subdivision)
                                                        .findFirst()
                                                        .orElse(null);
    subdivisionData.forEach((subdivision, data) -> {
      Node node = data.getNode();
      if (node != null) {
        node.pseudoClassStateChanged(NOTIFICATION_HIGHLIGHTED_PSEUDO_CLASS,
                                     subdivision.equals(currentPlayerSubdivision));
      }
    });

  }

  private Data<String, Integer> createSubdivisionData(Subdivision subdivision) {
    Data<String, Integer> data = new Data<>();
    data.setXValue(i18n.get("leagues.divisionName.%s".formatted(subdivision.division().nameKey())));
    data.setYValue(0);


    AnchorPane node = new AnchorPane();
    node.setAccessibleRole(AccessibleRole.TEXT);
    node.setAccessibleRoleDescription("Bar");
    node.focusTraversableProperty().bind(Platform.accessibilityActiveProperty());
    data.setNode(node);

    Label label = new Label();
    label.setText(subdivision.nameKey());
    label.setTextFill(Color.WHITE);
    label.setAlignment(Pos.CENTER);
    label.minWidthProperty().bind(node.widthProperty());
    AnchorPane.setBottomAnchor(label, 0d);
    node.getChildren().add(label);

    return data;
  }

  public List<LeagueEntry> getLeagueEntries() {
    return leagueEntries.get();
  }

  public ObjectProperty<List<LeagueEntry>> leagueEntriesProperty() {
    return leagueEntries;
  }

  public void setLeagueEntries(List<LeagueEntry> leagueEntries) {
    this.leagueEntries.set(List.copyOf(leagueEntries));
  }

  public List<Subdivision> getSubdivisions() {
    return subdivisions.get();
  }

  public ObjectProperty<List<Subdivision>> subdivisionsProperty() {
    return subdivisions;
  }

  public void setSubdivisions(List<Subdivision> subdivisions) {
    this.subdivisions.set(subdivisions);
  }
}
