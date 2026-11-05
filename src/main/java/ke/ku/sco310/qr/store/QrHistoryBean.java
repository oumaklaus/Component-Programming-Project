package ke.ku.sco310.qr.store;

import ke.ku.sco310.qr.api.BoundPropertySource;
import ke.ku.sco310.qr.api.QrHistoryEntry;

import java.beans.IndexedPropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.beans.VetoableChangeListener;
import java.beans.VetoableChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** In-memory history component whose main property is an indexed property. */
public class QrHistoryBean implements Serializable, BoundPropertySource {

    private static final long serialVersionUID = 1L;

    /** Component instance identifier. */
    public static final String COMPONENT_ID = "history.bounded-memory.v1";

    private transient PropertyChangeSupport propertyChanges = new PropertyChangeSupport(this);
    private transient VetoableChangeSupport vetoableChanges = new VetoableChangeSupport(this);

    private ArrayList<QrHistoryEntry> entries = new ArrayList<>();
    private int maxEntries = 50;
    private int discardedEntryCount = 0;

    public QrHistoryBean() {
    }

    public QrHistoryBean(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    public String getComponentId() {
        return COMPONENT_ID;
    }

    // ------------------------------------------------------------------ indexed property

    /** @return a copy of the whole indexed property. */
    public QrHistoryEntry[] getEntries() {
        return entries.toArray(new QrHistoryEntry[0]);
    }

    /** Replaces the whole indexed property. */
    public void setEntries(QrHistoryEntry[] newEntries) {
        ArrayList<QrHistoryEntry> replacement = new ArrayList<>();
        if (newEntries != null) {
            Collections.addAll(replacement, newEntries);
        }
        trim(replacement);
        QrHistoryEntry[] old = getEntries();
        this.entries = replacement;
        propertyChanges.firePropertyChange("entries", old, getEntries());
    }

    /**
     * @param index position of the entry
     * @return the entry stored at that position
     */
    public QrHistoryEntry getEntries(int index) {
        return entries.get(index);
    }

    /** Replaces one slot of the indexed property and fires an indexed property change event. */
    public void setEntries(int index, QrHistoryEntry entry) {
        QrHistoryEntry old = entries.set(index, entry);
        propertyChanges.fireIndexedPropertyChange("entries", index, old, entry);
    }

    /** @return number of retained entries (read-only simple property). */
    public int getEntryCount() {
        return entries.size();
    }

    // ------------------------------------------------------------------ list operations

    /**
     * Appends one entry, oldest-first ordering preserved, and applies the capacity policy.
     *
     * @param entry entry to append
     */
    public void addEntry(QrHistoryEntry entry) {
        if (entry == null) {
            return;
        }
        int index = entries.size();
        entries.add(entry);
        propertyChanges.fireIndexedPropertyChange("entries", index, null, entry);
        if (entries.size() > maxEntries) {
            QrHistoryEntry discarded = entries.remove(0);
            discardedEntryCount++;
            propertyChanges.fireIndexedPropertyChange("entries", 0, discarded, null);
        }
        propertyChanges.firePropertyChange("entryCount", index, entries.size());
    }

    /** @param index position to delete @return the removed entry */
    public QrHistoryEntry removeEntry(int index) {
        QrHistoryEntry removed = entries.remove(index);
        propertyChanges.fireIndexedPropertyChange("entries", index, removed, null);
        return removed;
    }

    /** Removes every entry but keeps the configured capacity. */
    public void clear() {
        QrHistoryEntry[] old = getEntries();
        entries.clear();
        propertyChanges.firePropertyChange("entries", old, getEntries());
    }

    /** @return the most recently added entry, or {@code null} when the history is empty. */
    public QrHistoryEntry getMostRecentEntry() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    /** @return an immutable snapshot for report generation and for the repository component. */
    public List<QrHistoryEntry> asList() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    /** Replaces the content from a persisted list without firing a capacity overflow. */
    public void restoreFrom(List<QrHistoryEntry> persisted) {
        entries = new ArrayList<>(persisted);
        trim(entries);
    }

    private void trim(ArrayList<QrHistoryEntry> target) {
        while (target.size() > maxEntries) {
            target.remove(0);
            discardedEntryCount++;
        }
    }

    // ------------------------------------------------------------------ properties

    public int getMaxEntries() {
        return maxEntries;
    }

    /** Constrained property: a listener can veto shrinking the capacity, for example because an audit process is still reading older entries. */
    public void setMaxEntries(int maxEntries) throws java.beans.PropertyVetoException {
        if (maxEntries < 1 || maxEntries > 10000) {
            throw new IllegalArgumentException("maxEntries must be 1..10000");
        }
        int old = this.maxEntries;
        vetoableChanges.fireVetoableChange("maxEntries", old, maxEntries);
        this.maxEntries = maxEntries;
        propertyChanges.firePropertyChange("maxEntries", old, maxEntries);
        if (entries.size() > maxEntries) {
            setEntries(getEntries());
        }
    }

    /** @return how many entries were dropped because the capacity was exceeded. */
    public int getDiscardedEntryCount() {
        return discardedEntryCount;
    }

    /** @return {@code true} when the capacity policy already dropped entries. */
    public boolean isOverflowed() {
        return discardedEntryCount > 0;
    }

    /** @return newline separated summaries, ready for a report appendix. */
    public String describeEntries() {
        if (entries.isEmpty()) {
            return "(history is empty)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            sb.append(String.format("%3d. %s%n", i + 1, entries.get(i).getSummary()));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ listener plumbing

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChanges.removePropertyChangeListener(listener);
    }

    public void addVetoableChangeListener(VetoableChangeListener listener) {
        vetoableChanges.addVetoableChangeListener(listener);
    }

    public void removeVetoableChangeListener(VetoableChangeListener listener) {
        vetoableChanges.removeVetoableChangeListener(listener);
    }

    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        propertyChanges = new PropertyChangeSupport(this);
        vetoableChanges = new VetoableChangeSupport(this);
    }

    @Override
    public String toString() {
        return "QrHistoryBean{entries=" + entries.size() + "/" + maxEntries
                + ", discarded=" + discardedEntryCount + "}";
    }
}
