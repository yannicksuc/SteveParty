package fr.lordfinn.steveparty.client.access;

public interface SmoothFlipState {
    float getFlipProgress();      // 0 = upright, 1 = fully upside-down
    void setFlipProgress(float progress);
}