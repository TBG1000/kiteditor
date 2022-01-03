package rip.bolt.kiteditor;

public class CustomKit {

    private int[] slots = new int[36];

    private int lastSlot;
    private boolean editing;

    public CustomKit() {
        for (int i = 0; i < slots.length; i++)
            slots[i] = i;
    }

    public void startEditing() {
        this.lastSlot = -1;
        this.editing = true;
    }

    public void stopEditing() {
        this.editing = false;
    }

    public boolean isEditing() {
        return editing;
    }

    public int getLastSlot() {
        return lastSlot;
    }

    public void setLastSlot(int lastSlot) {
        this.lastSlot = lastSlot;
    }

    public int[] getSlots() {
        return slots;
    }

}