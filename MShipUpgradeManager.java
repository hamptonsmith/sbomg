package com.shieldsbetter.frontierorbit.client.gui.model.shiphome;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class MShipUpgradeManager {
  private final List<EventListener> myListeners = new LinkedList<>();

  private final List<AvailableModulesKey> myAvailableModulesList = new LinkedList<>();

  private List<ConfigurationKey> myConfigurationList = new LinkedList<>();

  public MShipUpgradeManager(Iterable<ConfigurationRecord> configurationElements) {
    for (ConfigurationRecord e : configurationElements) {
      myConfigurationList.add(new ConfigurationKey(e));
    }
  }

  public Subscription addListener(final Listener l) {
    EventListener el = new EventListener() {
      @Override public void on(Event e) {
        e.on(l);
      }
    } ;
    return addListener(el);
  }

  public Subscription addListener(final EventListener el) {
    myListeners.add(el);
    return new Subscription() {
      @Override public void unsubscribe() {
        myListeners.remove(el);
      }
    } ;
  }

  public void build(final Listener l) {
     {
      int i = 0;
      for (AvailableModulesKey s : myAvailableModulesList) {
        l.onAvailableModulesAdded(this, s.getValue(), i, s);
        i++;
      }
    }
     {
      List<ConfigurationRecord> valueList = new LinkedList<>();
      for (ConfigurationKey s : myConfigurationList) {
        valueList.add(s.getValue());
      }
      l.onConfigurationReplaced(this, Collections.unmodifiableList(valueList));
    }
  }

  public AvailableModulesRecord getAvailableModulesElement(final int index) {
    return myAvailableModulesList.get(index).getValue();
  }

  public AvailableModulesRecord getAvailableModulesElement(final AvailableModulesKey key) {
    int index = myAvailableModulesList.indexOf(key);
    return myAvailableModulesList.get(index).getValue();
  }

  public Iterable<AvailableModulesRecord> availableModules() {
    return new Iterable<AvailableModulesRecord>() {
        @Override
        public Iterator<AvailableModulesRecord> iterator() {
                return new Iterator<AvailableModulesRecord>() {
              private final Iterator<AvailableModulesKey> myBaseIterator = myAvailableModulesList.iterator();

              @Override
              public boolean hasNext() {
                return myBaseIterator.hasNext();
              }

              @Override
              public void remove() {
                throw new UnsupportedOperationException();
              }

              @Override
              public AvailableModulesRecord next() {
                return myBaseIterator.next().getValue();
              }
            };
        }
      };
  }

  public void addAvailableModules(final AvailableModulesRecord newElement) {
    final AvailableModulesKey slot = new AvailableModulesKey(newElement);
    myAvailableModulesList.add(slot);
    final int addedAt = myAvailableModulesList.size() - 1;
    Event addEvent = new Event() {
      @Override
      public void on(Listener l) {
        l.onAvailableModulesAdded(MShipUpgradeManager.this, newElement, addedAt, slot);
      }
    };
    for (EventListener l : myListeners) {
      l.on(addEvent);
    }
  }

  public void removeAvailableModules(final int index) {
    AvailableModulesKey slot = myAvailableModulesList.remove(index);

    removeAvailableModules(index, slot);
  }

  public void removeAvailableModules(final AvailableModulesKey key) {
    int index = myAvailableModulesList.indexOf(key);
    if (index == -1) {
      throw new IllegalArgumentException();
    }

    removeAvailableModules(index, key);
  }

  private void removeAvailableModules(final int index, final AvailableModulesKey key) {
    myAvailableModulesList.remove(key);
    Event removeEvent = new Event() {
      @Override public void on(Listener l) {
        l.onAvailableModulesRemoved(MShipUpgradeManager.this, key.getValue(), index, key);
      }
    };
    for (EventListener l : myListeners) {
      l.on(removeEvent);
    }
  }

  public void setAvailableModules(final int index, final AvailableModulesRecord newValue) {
    AvailableModulesKey slot = myAvailableModulesList.get(index);

    setAvailableModules(index, slot, newValue);
  }

  public void setAvailableModules(final AvailableModulesKey key, final AvailableModulesRecord newValue) {
    int index = myAvailableModulesList.indexOf(key);
    if (index == -1) {
      throw new IllegalArgumentException();
    }

    setAvailableModules(index, key, newValue);
  }

  private void setAvailableModules(final int index, final AvailableModulesKey key, final AvailableModulesRecord value) {
    final AvailableModulesRecord oldType = key.getValue();
    key.setValue(value);
    Event addEvent = new Event() {
      @Override public void on(Listener l) {
        l.onAvailableModulesSet(MShipUpgradeManager.this, oldType, value, index, key);
      }
    };
    for (EventListener l : myListeners) {
      l.on(addEvent);
    }
  }

  public ConfigurationRecord getConfigurationElement(final int index) {
    return myConfigurationList.get(index).getValue();
  }

  public ConfigurationRecord getConfigurationElement(final ConfigurationKey key) {
    int index = myConfigurationList.indexOf(key);
    return myConfigurationList.get(index).getValue();
  }

  public Iterable<ConfigurationRecord> configuration() {
    return new Iterable<ConfigurationRecord>() {
        @Override
        public Iterator<ConfigurationRecord> iterator() {
                return new Iterator<ConfigurationRecord>() {
              private final Iterator<ConfigurationKey> myBaseIterator = myConfigurationList.iterator();

              @Override
              public boolean hasNext() {
                return myBaseIterator.hasNext();
              }

              @Override
              public void remove() {
                throw new UnsupportedOperationException();
              }

              @Override
              public ConfigurationRecord next() {
                return myBaseIterator.next().getValue();
              }
            };
        }
      };
  }

  public void replaceConfiguration(final Iterable<ConfigurationRecord> elements) {
    if (elements == null) {
      throw new NullPointerException();
    }

    myConfigurationList = new LinkedList<>();
    final List<ConfigurationRecord> valueList = new LinkedList<>();
    for (ConfigurationRecord e : elements) {
      valueList.add(e);
      myConfigurationList.add(new ConfigurationKey(e));
    }

    Event replaceEvent = new Event() {
      @Override public void on(Listener l) {
        l.onConfigurationReplaced(MShipUpgradeManager.this, java.util.Collections.unmodifiableList(valueList));
      }
    };
    for (EventListener l : myListeners) {
      l.on(replaceEvent);
    }
  }

  public interface Listener {
    void onConfigurationReplaced(MShipUpgradeManager source, List<ConfigurationRecord> newValue);

    void onAvailableModulesAdded(MShipUpgradeManager source, AvailableModulesRecord addedElement, int index, AvailableModulesKey key);

    void onAvailableModulesSet(MShipUpgradeManager source, AvailableModulesRecord oldValue, AvailableModulesRecord newValue, int index, AvailableModulesKey key);

    void onAvailableModulesRemoved(MShipUpgradeManager source, AvailableModulesRecord removedElement, int index, AvailableModulesKey key);
  }

  public interface Event {
    void on(Listener l);
  }

  public interface EventListener {
    void on(Event e);
  }

  public interface Subscription {
    void unsubscribe();
  }

  public static class AvailableModulesRecord {
    private final String myKey;

    private final String myName;

    private final String myDescription;

    public AvailableModulesRecord(String key, String name, String description) {
      myKey = key;
      myName = name;
      myDescription = description;
    }

    public Subscription addListener(final Listener l) {
      EventListener el = new EventListener() {
        @Override public void on(Event e) {
          e.on(l);
        }
      } ;
      return addListener(el);
    }

    public Subscription addListener(final EventListener el) {
      retu