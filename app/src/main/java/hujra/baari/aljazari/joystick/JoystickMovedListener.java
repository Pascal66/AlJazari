package hujra.baari.aljazari.joystick;

public interface JoystickMovedListener {
    void OnMoved(int pan, int tilt);
    void OnReleased();
    void OnReturnedToCenter();
}

