package raptor.swt;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;

import raptor.Quadrant;
import raptor.Raptor;
import raptor.RaptorConnectorWindowItem;
import raptor.chat.ChatType;
import raptor.connector.Connector;
import raptor.pref.PreferenceKeys;
import raptor.service.ThreadService;
import raptor.service.GameService.GameInfo;
import raptor.service.GameService.GameServiceAdapter;
import raptor.service.GameService.GameServiceListener;
import raptor.swt.RaptorTable.RaptorTableAdapter;
import raptor.swt.chat.ChatConsoleWindowItem;
import raptor.swt.chat.ChatUtils;
import raptor.swt.chat.controller.PersonController;
import raptor.util.IntegerComparator;
import raptor.util.RaptorRunnable;
import raptor.util.RatingComparator;

public class GamesWindowItem implements RaptorConnectorWindowItem {

	public static final Quadrant[] MOVE_TO_QUADRANTS = { Quadrant.I,
			Quadrant.II, Quadrant.III, Quadrant.IV, Quadrant.V, Quadrant.VI,
			Quadrant.VII, Quadrant.VIII, Quadrant.IX };

	public static final String[] getRatings() {
		return new String[] { "0", "1", "700", "1000", "1100", "1200", "1300",
				"1400", "1500", "1600", "1700", "1800", "1900", "2000", "2100",
				"2200", "2300", "2400", "2500", "2600", "2700", "2800", "3000",
				"9999" };
	}

	public static enum GameInfoCategory {
		blitz, lightning, untimed, examined, standard, wild, atomic, crazyhouse, bughouse, losers, suicide, nonstandard
	};

	protected Connector connector;
	protected Composite composite;
	protected Combo minRatingsFilter;
	protected Combo maxRatingsFilter;
	protected Combo ratedFilter;
	protected Button isShowingPrivate;
	protected Button isShowingBughouse;
	protected Button isShowingLightning;
	protected Button isShowingBlitz;
	protected Button isShowingExamined;
	protected Button isShowingStandard;
	protected Button isShowingCrazyhouse;
	protected Button isShowingWild;
	protected Button isShowingAtomic;
	protected Button isShowingSuicide;
	protected Button isShowingLosers;
	protected Button isShowingUntimed;
	protected Button isShowingNonstandard;
	protected RaptorTable gamesTable;
	protected Composite settings;
	protected boolean isActive = false;

	protected Runnable timer = new Runnable() {
		public void run() {
			if (isActive) {
				if (connector != null && connector.isConnected()) {
					sendGamesMessage();
					ThreadService
							.getInstance()
							.scheduleOneShot(
									Raptor
											.getInstance()
											.getPreferences()
											.getInt(
													PreferenceKeys.APP_WINDOW_ITEM_POLL_INTERVAL) * 1000,
									this);
				}
			}
		}
	};

	protected GameServiceListener listener = new GameServiceAdapter() {
		@Override
		public void gameInfoChanged() {
			refreshGamesTable();
		}
	};

	public GamesWindowItem(Connector connector) {
		this.connector = connector;
		connector.getGameService().addGameServiceListener(listener);
	}

	public void addItemChangedListener(ItemChangedListener listener) {
	}

	/**
	 * Invoked after this control is moved to a new quadrant.
	 */
	public void afterQuadrantMove(Quadrant newQuadrant) {
		Raptor.getInstance().getPreferences().setValue(
				getConnector().getShortName() + "-"
						+ PreferenceKeys.GAMES_TAB_QUADRANT, newQuadrant);
	}

	public boolean confirmClose() {
		return true;
	}

	public void dispose() {
		isActive = false;
		composite.dispose();
		connector.getGameService().removeGameServiceListener(listener);
	}

	public Connector getConnector() {
		return connector;
	}

	public Control getControl() {
		return composite;
	}

	public Image getImage() {
		return null;
	}

	public Quadrant[] getMoveToQuadrants() {
		return MOVE_TO_QUADRANTS;
	}

	public Quadrant getPreferredQuadrant() {
		return Raptor.getInstance().getPreferences().getQuadrant(
				getConnector().getShortName() + "-"
						+ PreferenceKeys.GAMES_TAB_QUADRANT);
	}

	public String getTitle() {
		return getConnector().getShortName() + "(Games)";
	}

	public Control getToolbar(Composite parent) {
		return null;
	}

	public void init(Composite parent) {
		composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));

		final TabFolder tabFolder = new TabFolder(composite, SWT.BORDER);
		tabFolder.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		TabItem controlsTab = new TabItem(tabFolder, SWT.NONE);
		controlsTab.setText("Settings");
		settings = new Composite(tabFolder, SWT.NONE);
		settings.setLayout(new GridLayout(1, false));
		buildSettingsComposite(settings);
		controlsTab.setControl(settings);

		TabItem tableTab = new TabItem(tabFolder, SWT.NULL);
		tableTab.setText("Games");

		Composite tableComposite = new Composite(tabFolder, SWT.NONE);
		tableComposite.setLayout(new GridLayout(1, false));
		tableTab.setControl(tableComposite);

		gamesTable = new RaptorTable(tableComposite, SWT.BORDER | SWT.H_SCROLL
				| SWT.V_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION);
		gamesTable.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		gamesTable.addColumn("ID", SWT.LEFT, 10, true, new IntegerComparator());
		gamesTable
				.addColumn("W ELO", SWT.LEFT, 15, true, new RatingComparator());
		gamesTable.addColumn("White", SWT.LEFT, 20, true, null);
		gamesTable.addColumn("B ELO", SWT.LEFT, 15, true, null);
		gamesTable.addColumn("Black", SWT.LEFT, 20, true, null);
		gamesTable.addColumn("Type", SWT.LEFT, 20, true, null);

		// Sort once so when data is refreshed it will be on elo descending.
		gamesTable.sort(1);
		tableTab.setControl(tableComposite);

		gamesTable.addRaptorTableListener(new RaptorTableAdapter() {

			@Override
			public void rowDoubleClicked(MouseEvent event, String[] rowData) {
				getConnector().sendMessage("observe " + rowData[0], true);
			}

			@Override
			public void rowRightClicked(MouseEvent event, String[] rowData) {
				// Menu menu = new Menu(composite.getShell(), SWT.POP_UP);
				// addPersonMenuItems(menu, rowData[4]);
				// if (menu.getItemCount() > 0) {
				// menu.setLocation(gamesTable.getTable().toDisplay(event.x,
				// event.y));
				// menu.setVisible(true);
				// while (!menu.isDisposed() && menu.isVisible()) {
				// if (!composite.getDisplay().readAndDispatch()) {
				// composite.getDisplay().sleep();
				// }
				// }
				// }
				// menu.dispose();
			}
		});

		tabFolder.setSelection(Raptor.getInstance().getPreferences().getInt(
				PreferenceKeys.GAMES_TABLE_SELECTED_TAB));

		tabFolder.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.SEEK_TABLE_SELECTED_TAB,
						tabFolder.getSelectionIndex());
			}
		});
		sendGamesMessage();
		refreshGamesTable();
	}

	public void sendGamesMessage() {
		if (isActive && connector.isConnected()) {
			connector.sendMessage("$$games", true, ChatType.GAMES);
		}
	}

	public void onActivate() {
		if (!isActive) {
			isActive = true;
			sendGamesMessage();
			ThreadService
					.getInstance()
					.scheduleOneShot(
							Raptor
									.getInstance()
									.getPreferences()
									.getInt(
											PreferenceKeys.APP_WINDOW_ITEM_POLL_INTERVAL) * 1000,
							timer);
		}

	}

	public void onPassivate() {
		if (isActive) {
			isActive = false;
		}
	}

	public void removeItemChangedListener(ItemChangedListener listener) {
	}

	protected void addPersonMenuItems(Menu menu, String word) {
		if (getConnector().isLikelyPerson(word)) {
			if (menu.getItemCount() > 0) {
				new MenuItem(menu, SWT.SEPARATOR);
			}
			final String person = getConnector().parsePerson(word);
			MenuItem item = new MenuItem(menu, SWT.PUSH);
			item.setText("Add a tab for person: " + person);
			item.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event e) {
					if (!Raptor.getInstance().getWindow()
							.containsPersonalTellItem(getConnector(), person)) {
						ChatConsoleWindowItem windowItem = new ChatConsoleWindowItem(
								new PersonController(getConnector(), person));
						Raptor.getInstance().getWindow().addRaptorWindowItem(
								windowItem, false);
						ChatUtils.appendPreviousChatsToController(windowItem
								.getConsole());
					}
				}
			});

			final String[][] connectorPersonItems = getConnector()
					.getPersonActions(person);
			if (connectorPersonItems != null) {
				for (int i = 0; i < connectorPersonItems.length; i++) {
					if (connectorPersonItems[i][0].equals("separator")) {
						new MenuItem(menu, SWT.SEPARATOR);
					} else {
						item = new MenuItem(menu, SWT.PUSH);
						item.setText(connectorPersonItems[i][0]);
						final int index = i;
						item.addListener(SWT.Selection, new Listener() {
							public void handleEvent(Event e) {
								getConnector().sendMessage(
										connectorPersonItems[index][1]);
							}
						});
					}
				}
			}
		}
	}

	protected void buildSettingsComposite(Composite parent) {

		Composite ratingFilterComposite = new Composite(parent, SWT.NONE);
		ratingFilterComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER,
				true, false));
		ratingFilterComposite.setLayout(new RowLayout());
		minRatingsFilter = new Combo(ratingFilterComposite, SWT.DROP_DOWN
				| SWT.READ_ONLY);

		for (String rating : getRatings()) {
			minRatingsFilter.add(rating);
		}

		minRatingsFilter.select(Raptor.getInstance().getPreferences().getInt(
				PreferenceKeys.GAMES_TABLE_RATINGS_INDEX));
		minRatingsFilter.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_RATINGS_INDEX,
						minRatingsFilter.getSelectionIndex());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		RaptorLabel label = new RaptorLabel(ratingFilterComposite, SWT.LEFT);
		label.setText(">= Rating <=");
		maxRatingsFilter = new Combo(ratingFilterComposite, SWT.DROP_DOWN
				| SWT.READ_ONLY);
		for (String rating : getRatings()) {
			maxRatingsFilter.add(rating);
		}
		maxRatingsFilter.select(Raptor.getInstance().getPreferences().getInt(
				PreferenceKeys.GAMES_TABLE_MAX_RATINGS_INDEX));
		maxRatingsFilter.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_MAX_RATINGS_INDEX,
						maxRatingsFilter.getSelectionIndex());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		Composite ratedComposite = new Composite(parent, SWT.NONE);
		ratedComposite.setLayout(new RowLayout());
		ratedComposite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false));

		label = new RaptorLabel(ratedComposite, SWT.LEFT);
		label.setText("Rated:");
		ratedFilter = new Combo(ratedComposite, SWT.DROP_DOWN | SWT.READ_ONLY);
		ratedFilter.add("Rated and Unrated");
		ratedFilter.add("Rated");
		ratedFilter.add("Unrated");
		ratedFilter.select(Raptor.getInstance().getPreferences().getInt(
				PreferenceKeys.GAMES_TABLE_RATED_INDEX));
		ratedFilter.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_RATED_INDEX,
						ratedFilter.getSelectionIndex());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		Composite typeFilterComposite = new Composite(parent, SWT.NONE);
		typeFilterComposite.setLayout(new GridLayout(3, false));

		isShowingAtomic = new Button(typeFilterComposite, SWT.CHECK);
		isShowingAtomic.setText("Atomic");
		isShowingAtomic.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_ATOMIC));
		isShowingAtomic.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_ATOMIC,
						isShowingAtomic.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingBlitz = new Button(typeFilterComposite, SWT.CHECK);
		isShowingBlitz.setText("Blitz");
		isShowingBlitz.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_BLITZ));
		isShowingBlitz.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_BLITZ,
						isShowingBlitz.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingBughouse = new Button(typeFilterComposite, SWT.CHECK);
		isShowingBughouse.setText("Bughouse");
		isShowingBughouse.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_BUGHOUSE));
		isShowingBughouse.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_BUGHOUSE,
						isShowingBughouse.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingCrazyhouse = new Button(typeFilterComposite, SWT.CHECK);
		isShowingCrazyhouse.setText("Crazyhouse");
		isShowingCrazyhouse.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_CRAZYHOUSE));
		isShowingCrazyhouse.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_CRAZYHOUSE,
						isShowingCrazyhouse.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingExamined = new Button(typeFilterComposite, SWT.CHECK);
		isShowingExamined.setText("Examined");
		isShowingExamined.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_EXAMINED));
		isShowingExamined.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_EXAMINED,
						isShowingExamined.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingLightning = new Button(typeFilterComposite, SWT.CHECK);
		isShowingLightning.setText("Lightning");
		isShowingLightning.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_LIGHTNING));
		isShowingLightning.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_LIGHTNING,
						isShowingLightning.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingLosers = new Button(typeFilterComposite, SWT.CHECK);
		isShowingLosers.setText("Losers");
		isShowingLosers.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_LOSERS));
		isShowingLosers.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_LOSERS,
						isShowingLosers.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingNonstandard = new Button(typeFilterComposite, SWT.CHECK);
		isShowingNonstandard.setText("Nonstandard");
		isShowingNonstandard.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_NONSTANDARD));
		isShowingNonstandard.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_NONSTANDARD,
						isShowingNonstandard.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingPrivate = new Button(typeFilterComposite, SWT.CHECK);
		isShowingPrivate.setText("Private");
		isShowingPrivate.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_PRIVATE));
		isShowingPrivate.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_PRIVATE,
						isShowingPrivate.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingSuicide = new Button(typeFilterComposite, SWT.CHECK);
		isShowingSuicide.setText("Suicide");
		isShowingSuicide.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_SUICIDE));
		isShowingSuicide.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_SUICIDE,
						isShowingSuicide.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingStandard = new Button(typeFilterComposite, SWT.CHECK);
		isShowingStandard.setText("Standard");
		isShowingStandard.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_STANDARD));
		isShowingStandard.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_STANDARD,
						isShowingStandard.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingUntimed = new Button(typeFilterComposite, SWT.CHECK);
		isShowingUntimed.setText("Untimed");
		isShowingUntimed.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_UNTIMED));
		isShowingUntimed.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_UNTIMED,
						isShowingUntimed.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});

		isShowingWild = new Button(typeFilterComposite, SWT.CHECK);
		isShowingWild.setText("Wild");
		isShowingWild.setSelection(Raptor.getInstance().getPreferences()
				.getBoolean(PreferenceKeys.GAMES_TABLE_SHOW_WILD));
		isShowingWild.addSelectionListener(new SelectionListener() {
			public void widgetDefaultSelected(SelectionEvent e) {
			}

			public void widgetSelected(SelectionEvent e) {
				Raptor.getInstance().getPreferences().setValue(
						PreferenceKeys.GAMES_TABLE_SHOW_WILD,
						isShowingWild.getSelection());
				Raptor.getInstance().getPreferences().save();
				refreshGamesTable();
			}
		});
	}

	protected GameInfo[] getFilteredGameInfo() {
		GameInfo[] gameInfos = connector.getGameService().getGameInfos();
		if (gameInfos == null) {
			gameInfos = new GameInfo[0];
		}
		List<GameInfo> result = new ArrayList<GameInfo>(gameInfos.length);
		for (GameInfo info : gameInfos) {
			if (passesFilterCriteria(info)) {
				result.add(info);
			}
		}
		return result.toArray(new GameInfo[0]);
	}

	protected boolean passesFilterCriteria(GameInfo info) {
		boolean result = true;
		int minFilterRating = Integer.parseInt(minRatingsFilter.getText());
		int maxFilterRating = Integer.parseInt(maxRatingsFilter.getText());
		if (minFilterRating >= maxFilterRating) {
			int tmp = maxFilterRating;
			maxFilterRating = minFilterRating;
			minFilterRating = tmp;
		}
		int whiteElo = 0;
		int blackElo = 0;
		try {
			whiteElo = Integer.parseInt(info.getWhiteElo());
		} catch (NumberFormatException nfe) {
		}
		try {
			blackElo = Integer.parseInt(info.getBlackElo());
		} catch (NumberFormatException nfe) {
		}

		if (whiteElo >= minFilterRating && whiteElo <= maxFilterRating
				&& blackElo >= minFilterRating && blackElo <= maxFilterRating) {
			if (ratedFilter.getSelectionIndex() == 1) {
				result = info.isRated();
			} else if (ratedFilter.getSelectionIndex() == 2) {
				result = !info.isRated();
			}
			if (result) {
				if (!isShowingPrivate.getSelection()) {
					result = !info.isPrivate();
				}
				if (result) {
					switch (info.getCategory()) {
					case standard:
						result = isShowingStandard.getSelection();
						break;
					case blitz:
						result = isShowingBlitz.getSelection();
						break;
					case lightning:
						result = isShowingLightning.getSelection();
						break;
					case atomic:
						result = isShowingAtomic.getSelection();
						break;
					case suicide:
						result = isShowingSuicide.getSelection();
						break;
					case losers:
						result = isShowingLosers.getSelection();
						break;
					case examined:
						result = isShowingExamined.getSelection();
						break;
					case wild:
						result = isShowingWild.getSelection();
						break;
					case crazyhouse:
						result = isShowingCrazyhouse.getSelection();
						break;
					case untimed:
						result = isShowingUntimed.getSelection();
						break;
					case bughouse:
						result = isShowingBughouse.getSelection();
						break;
					case nonstandard:
						result = isShowingNonstandard.getSelection();
						break;
					}
				}
			}
		} else {
			result = false;
		}
		return result;
	}

	protected void refreshGamesTable() {
		Raptor.getInstance().getDisplay().asyncExec(new RaptorRunnable() {
			@Override
			public void execute() {
				if (gamesTable.isDisposed()) {
					return;
				}
				synchronized (gamesTable.getTable()) {
					GameInfo[] gameInfos = getFilteredGameInfo();
					String[][] data = new String[gameInfos.length][6];

					for (int i = 0; i < data.length; i++) {
						GameInfo info = gameInfos[i];
						data[i][0] = info.getId();
						data[i][1] = info.getWhiteElo();
						data[i][2] = info.getWhiteName();
						data[i][3] = info.getBlackElo();
						data[i][4] = info.getBlackName();
						data[i][5] = info.getCategory().toString();
					}
					gamesTable.refreshTable(data);
				}
			}
		});
	}
}
