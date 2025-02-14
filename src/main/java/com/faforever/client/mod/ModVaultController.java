package com.faforever.client.mod;

import com.faforever.client.domain.api.ModType;
import com.faforever.client.domain.api.ModVersion;
import com.faforever.client.fx.FxApplicationThreadExecutor;
import com.faforever.client.fx.JavaFxUtil;
import com.faforever.client.fx.PlatformService;
import com.faforever.client.i18n.I18n;
import com.faforever.client.main.event.NavigateEvent;
import com.faforever.client.main.event.OpenModVaultEvent;
import com.faforever.client.notification.NotificationService;
import com.faforever.client.preferences.ForgedAlliancePrefs;
import com.faforever.client.preferences.ModSearchPrefs;
import com.faforever.client.preferences.VaultPrefs;
import com.faforever.client.query.BinaryFilterController;
import com.faforever.client.query.DateRangeFilterController;
import com.faforever.client.query.RangeFilterController;
import com.faforever.client.query.SearchablePropertyMappings;
import com.faforever.client.query.TextFilterController;
import com.faforever.client.query.ToggleFilterController;
import com.faforever.client.reporting.ReportingService;
import com.faforever.client.theme.UiService;
import com.faforever.client.ui.dialog.Dialog;
import com.faforever.client.vault.VaultEntityCardController;
import com.faforever.client.vault.VaultEntityController;
import com.faforever.client.vault.search.SearchController.SearchConfig;
import javafx.scene.Node;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class ModVaultController extends VaultEntityController<ModVersion> {

  private final ModService modService;
  private final PlatformService platformService;
  private final VaultPrefs vaultPrefs;
  private final ForgedAlliancePrefs forgedAlliancePrefs;

  private ModDetailController modDetailController;
  private Integer recommendedShowRoomPageCount;

  public ModVaultController(ModService modService, I18n i18n,
                            UiService uiService, NotificationService notificationService,
                            ReportingService reportingService,
                            PlatformService platformService, VaultPrefs vaultPrefs,
                            ForgedAlliancePrefs forgedAlliancePrefs,
                            FxApplicationThreadExecutor fxApplicationThreadExecutor) {
    super(uiService, notificationService, i18n, reportingService, vaultPrefs, fxApplicationThreadExecutor);
    this.modService = modService;
    this.platformService = platformService;
    this.vaultPrefs = vaultPrefs;
    this.forgedAlliancePrefs = forgedAlliancePrefs;
  }

  @Override
  protected void onInitialize() {
    super.onInitialize();
    JavaFxUtil.fixScrollSpeed(scrollPane);

    manageVaultButton.setVisible(true);
    manageVaultButton.setText(i18n.get("modVault.manageMods"));
    modService.getRecommendedModPageCount(TOP_ELEMENT_COUNT)
              .subscribe(pageCount -> recommendedShowRoomPageCount = pageCount);
  }

  @Override
  protected void onDisplayDetails(ModVersion modVersion) {
    fxApplicationThreadExecutor.execute(() -> {
      modDetailController.setModVersion(modVersion);
      modDetailController.getRoot().setVisible(true);
      modDetailController.getRoot().requestFocus();
    });
  }

  @Override
  protected void setSupplier(SearchConfig searchConfig) {
    switch (searchType) {
      case SEARCH ->
          currentSupplier = modService.findByQueryWithPageCount(searchConfig, pageSize, pagination.getCurrentPageIndex() + 1);
      case NEWEST ->
          currentSupplier = modService.getNewestModsWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case HIGHEST_RATED ->
          currentSupplier = modService.getHighestRatedModsWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case HIGHEST_RATED_UI ->
          currentSupplier = modService.getHighestRatedUiModsWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
      case RECOMMENDED ->
          currentSupplier = modService.getRecommendedModsWithPageCount(pageSize, pagination.getCurrentPageIndex() + 1);
    }
  }

  @Override
  protected VaultEntityCardController<ModVersion> createEntityCard() {
    ModCardController controller = uiService.loadFxml("theme/vault/mod/mod_card.fxml");
    controller.setOnOpenDetailListener(this::onDisplayDetails);
    return controller;
  }

  @Override
  protected List<ShowRoomCategory<ModVersion>> getShowRoomCategories() {
    return List.of(
        new ShowRoomCategory<>(() -> {
          int recommendedPage;
          if (recommendedShowRoomPageCount != null && recommendedShowRoomPageCount > 0) {
            recommendedPage = new Random().nextInt(recommendedShowRoomPageCount) + 1;
          } else {
            recommendedPage = 1;
          }
          return modService.getRecommendedModsWithPageCount(TOP_ELEMENT_COUNT, recommendedPage);
        }, SearchType.RECOMMENDED, "modVault.recommended"),
        new ShowRoomCategory<>(() -> modService.getHighestRatedModsWithPageCount(TOP_ELEMENT_COUNT, 1),
                               SearchType.HIGHEST_RATED, "modVault.highestRated"),
        new ShowRoomCategory<>(() -> modService.getHighestRatedUiModsWithPageCount(TOP_ELEMENT_COUNT, 1),
                               SearchType.HIGHEST_RATED_UI, "modVault.highestRatedUiMods"),
        new ShowRoomCategory<>(() -> modService.getNewestModsWithPageCount(TOP_ELEMENT_COUNT, 1), SearchType.NEWEST,
                               "modVault.newestMods")
    );
  }

  @Override
  public void onUploadButtonClicked() {
    platformService.askForPath(i18n.get("modVault.upload.chooseDirectory"), forgedAlliancePrefs.getModsDirectory())
                   .thenAccept(possiblePath -> possiblePath.ifPresent(this::openUploadWindow));
  }

  @Override
  protected void onManageVaultButtonClicked() {
    ModManagerController modManagerController = uiService.loadFxml("theme/mod_manager.fxml");
    Dialog dialog = uiService.showInDialog(vaultRoot, modManagerController.getRoot(), i18n.get("modVault.modManager"));
    dialog.setOnDialogClosed(event -> modManagerController.apply());
    modManagerController.setOnCloseButtonClickedListener(dialog::close);
  }

  @Override
  protected Node getDetailView() {
    modDetailController = uiService.loadFxml("theme/vault/mod/mod_detail.fxml");
    return modDetailController.getRoot();
  }

  @Override
  protected void initSearchController() {
    searchController.setRootType(com.faforever.commons.api.dto.Mod.class);
    searchController.setSearchableProperties(SearchablePropertyMappings.MOD_PROPERTY_MAPPING);
    searchController.setSortConfig(vaultPrefs.mapSortConfigProperty());
    searchController.setOnlyShowLastYearCheckBoxVisible(false);
    searchController.setVaultRoot(vaultRoot);
    searchController.setSavedQueries(vaultPrefs.getSavedModQueries());

    ModSearchPrefs modSearch = vaultPrefs.getModSearch();

    TextFilterController textFilterController = searchController.addTextFilter("displayName", i18n.get("mod.displayName"), false);
    textFilterController.setText(modSearch.getModNameField());
    modSearch.modNameFieldProperty().bind(textFilterController.textFieldProperty().when(showing));
    textFilterController = searchController.addTextFilter("author", i18n.get("mod.author"), false);
    textFilterController.setText(modSearch.getModAuthorField());
    modSearch.modAuthorFieldProperty().bind(textFilterController.textFieldProperty().when(showing));

    DateRangeFilterController dateRangeFilterController = searchController.addDateRangeFilter("latestVersion.updateTime", i18n.get("mod.uploadedDateTime"), 0);
    dateRangeFilterController.setBeforeDate(modSearch.getUploadedBeforeDate());
    dateRangeFilterController.setAfterDate(modSearch.getUploadedAfterDate());
    modSearch.uploadedBeforeDateProperty().bind(dateRangeFilterController.beforeDateProperty().when(showing));
    modSearch.uploadedAfterDateProperty().bind(dateRangeFilterController.afterDateProperty().when(showing));

    RangeFilterController rangeFilter = searchController.addRangeFilter("reviewsSummary.averageScore", i18n.get("reviews.averageScore"), 0, 5, 10, 4, 1);
    rangeFilter.setLowValue(modSearch.getAverageReviewScoresMin());
    rangeFilter.setHighValue(modSearch.getAverageReviewScoresMax());
    modSearch.averageReviewScoresMinProperty().bind(rangeFilter.lowValueProperty().asObject().when(showing));
    modSearch.averageReviewScoresMaxProperty().bind(rangeFilter.highValueProperty().asObject().when(showing));


    BinaryFilterController binaryFilter = searchController.addBinaryFilter("latestVersion.type", i18n.get("mod.type"),
        ModType.UI.toString(), ModType.SIM.toString(), i18n.get("modType.ui"), i18n.get("modType.sim"));
    binaryFilter.setFirstSelected(modSearch.getUiMod());
    binaryFilter.setSecondSelected(modSearch.getSimMod());
    modSearch.uiModProperty().bind(binaryFilter.firstSelectedProperty().when(showing));
    modSearch.simModProperty().bind(binaryFilter.secondSelectedProperty().when(showing));

    ToggleFilterController toggleFilterController = searchController.addToggleFilter("latestVersion.ranked", i18n.get("mod.onlyRanked"), "true");
    toggleFilterController.setSelected(modSearch.getOnlyRanked());
    modSearch.onlyRankedProperty().bind(toggleFilterController.selectedProperty().when(showing));
  }

  @Override
  protected Class<? extends NavigateEvent> getDefaultNavigateEvent() {
    return OpenModVaultEvent.class;
  }

  @Override
  protected void handleSpecialNavigateEvent(NavigateEvent navigateEvent) {
    log.warn("No such NavigateEvent for this Controller: {}", navigateEvent.getClass());
  }

  private void openUploadWindow(Path path) {
    ModUploadController modUploadController = uiService.loadFxml("theme/vault/mod/mod_upload.fxml");
    modUploadController.setModPath(path);

    Node root = modUploadController.getRoot();
    Dialog dialog = uiService.showInDialog(vaultRoot, root, i18n.get("modVault.upload.title"));
    modUploadController.setUploadListener(this::onRefreshButtonClicked);
    modUploadController.setOnCancelButtonClickedListener(dialog::close);
  }
}
