
package proc.core;

import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;


import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.regex.*;
import java.util.zip.*;

import processing.data.*;
import processing.event.*;
import processing.opengl.*;


/**
 * Base class for all sketches that use processing.core.
 * <p/>
 * The <A HREF="https://github.com/processing/processing/wiki/Window-Size-and-Full-Screen">
 * Window Size and Full Screen</A> page on the Wiki has useful information
 * about sizing, multiple displays, full screen, etc.
 * <p/>
 * Processing uses active mode rendering. All animation tasks happen on the
 * "Processing Animation Thread". The setup() and draw() methods are handled
 * by that thread, and events (like mouse movement and key presses, which are
 * fired by the event dispatch thread or EDT) are queued to be safely handled
 * at the end of draw().
 * <p/>
 * Starting with 3.0a6, blit operations are on the EDT, so as not to cause
 * GUI problems with Swing and AWT. In the case of the default renderer, the
 * sketch renders to an offscreen image, then the EDT is asked to bring that
 * image to the screen.
 * <p/>
 * For code that needs to run on the EDT, use EventQueue.invokeLater(). When
 * doing so, be careful to synchronize between that code and the Processing
 * animation thread. That is, you can't call Processing methods from the EDT
 * or at any random time from another thread. Use of a callback function or
 * the registerXxx() methods in PApplet can help ensure that your code doesn't
 * do something naughty.
 * <p/>
 * As of Processing 3.0, we have removed Applet as the base class for PApplet.
 * This means that we can remove lots of legacy code, however one downside is
 * that it's no longer possible (without extra code) to embed a PApplet into
 * another Java application.
 * <p/>
 * As of Processing 3.0, we have discontinued support for versions of Java
 * prior to 1.8. We don't have enough people to support it, and for a
 * project of our (tiny) size, we should be focusing on the future, rather
 * than working around legacy Java code.
 */
public class PApplet implements PConstants {
  /** Full name of the Java version (i.e. 1.5.0_11). */
  static public final String javaVersionName =
    System.getProperty("java.version");

  static public final int javaPlatform;
  static {
    String version = javaVersionName;
    if (javaVersionName.startsWith("1.")) {
      version = version.substring(2);
    }
    javaPlatform = parseInt(version.substring(0, version.indexOf('.')));
  }

  /**
   * Do not use; javaPlatform or javaVersionName are better options.
   * For instance, javaPlatform is useful when you need a number for
   * comparison, i.e. "if (javaPlatform >= 9)".
   * @deprecated
   */
  @Deprecated
  public static final float javaVersion = 1 + javaPlatform / 10f;

  /**
   * Current platform in use, one of the
   * PConstants WINDOWS, MACOSX, MACOS9, LINUX or OTHER.
   */
  static public int platform;

  static {
    String osname = System.getProperty("os.name");

    if (osname.indexOf("Mac") != -1) {
      platform = MACOSX;

    } else if (osname.indexOf("Windows") != -1) {
      platform = WINDOWS;

    } else if (osname.equals("Linux")) {  // true for the ibm vm
      platform = LINUX;

    } else {
      platform = OTHER;
    }
  }

  /**
   * Whether to use native (AWT) dialogs for selectInput and selectOutput.
   * The native dialogs on some platforms can be ugly, buggy, or missing
   * features. For 3.3.5, this defaults to true on all platforms.
   */
  static public boolean useNativeSelect = true;

  /** The PGraphics renderer associated with this PApplet */
  public PGraphics g;

  /**
   * ( begin auto-generated from displayWidth.xml )
   *
   * System variable which stores the width of the computer screen. For
   * example, if the current screen resolution is 1024x768,
   * <b>displayWidth</b> is 1024 and <b>displayHeight</b> is 768. These
   * dimensions are useful when exporting full-screen applications.
   * <br /><br />
   * To ensure that the sketch takes over the entire screen, use "Present"
   * instead of "Run". Otherwise the window will still have a frame border
   * around it and not be placed in the upper corner of the screen. On Mac OS
   * X, the menu bar will remain present unless "Present" mode is used.
   *
   * ( end auto-generated )
   */
  public int displayWidth;

  /**
   * ( begin auto-generated from displayHeight.xml )
   *
   * System variable that stores the height of the computer screen. For
   * example, if the current screen resolution is 1024x768,
   * <b>displayWidth</b> is 1024 and <b>displayHeight</b> is 768. These
   * dimensions are useful when exporting full-screen applications.
   * <br /><br />
   * To ensure that the sketch takes over the entire screen, use "Present"
   * instead of "Run". Otherwise the window will still have a frame border
   * around it and not be placed in the upper corner of the screen. On Mac OS
   * X, the menu bar will remain present unless "Present" mode is used.
   *
   * ( end auto-generated )
   */
  public int displayHeight;

  /** A leech graphics object that is echoing all events. */
  public PGraphics recorder;

  /**
   * Command line options passed in from main().
   * This does not include the arguments passed in to PApplet itself.
   * @see PApplet#main
   */
  public String[] args;

  /**
   * Path to sketch folder. Previously undocumented, made private in 3.0a5
   * so that people use the sketchPath() method and it's inited properly.
   * Call sketchPath() once to set the default.
   */
  private String sketchPath;
//  public String sketchPath;

  static final boolean DEBUG = false;
//  static final boolean DEBUG = true;

  /** Default width and height for sketch when not specified */
  static public final int DEFAULT_WIDTH = 100;
  static public final int DEFAULT_HEIGHT = 100;

//  /**
//   * Exception thrown when size() is called the first time.
//   * <p>
//   * This is used internally so that setup() is forced to run twice
//   * when the renderer is changed. This is the only way for us to handle
//   * invoking the new renderer while also in the midst of rendering.
//   */
//  static public class RendererChangeException extends RuntimeException { }

  /**
   * true if no size() command has been executed. This is used to wait until
   * a size has been set before placing in the window and showing it.
   */
//  public boolean defaultSize;

//  /** Storage for the current renderer size to avoid re-allocation. */
//  Dimension currentSize = new Dimension();

  /**
   * ( begin auto-generated from pixels.xml )
   *
   * Array containing the values for all the pixels in the display window.
   * These values are of the color datatype. This array is the size of the
   * display window. For example, if the image is 100x100 pixels, there will
   * be 10000 values and if the window is 200x300 pixels, there will be 60000
   * values. The <b>index</b> value defines the position of a value within
   * the array. For example, the statement <b>color b = pixels[230]</b> will
   * set the variable <b>b</b> to be equal to the value at that location in
   * the array.<br />
   * <br />
   * Before accessing this array, the data must loaded with the
   * <b>loadPixels()</b> function. After the array data has been modified,
   * the <b>updatePixels()</b> function must be run to update the changes.
   * Without <b>loadPixels()</b>, running the code may (or will in future
   * releases) result in a NullPointerException.
   *
   * ( end auto-generated )
   *
   * @webref image:pixels
   * @see PApplet#loadPixels()
   * @see PApplet#updatePixels()
   * @see PApplet#get(int, int, int, int)
   * @see PApplet#set(int, int, int)
   * @see PImage
   */
  public int[] pixels;

  /**
   * ( begin auto-generated from width.xml )
   *
   * System variable which stores the width of the display window. This value
   * is set by the first parameter of the <b>size()</b> function. For
   * example, the function call <b>size(320, 240)</b> sets the <b>width</b>
   * variable to the value 320. The value of <b>width</b> is zero until
   * <b>size()</b> is called.
   *
   * ( end auto-generated )
   * @webref environment
   * @see PApplet#height
   * @see PApplet#size(int, int)
   */
  public int width = DEFAULT_WIDTH;

  /**
   * ( begin auto-generated from height.xml )
   *
   * System variable which stores the height of the display window. This
   * value is set by the second parameter of the <b>size()</b> function. For
   * example, the function call <b>size(320, 240)</b> sets the <b>height</b>
   * variable to the value 240. The value of <b>height</b> is zero until
   * <b>size()</b> is called.
   *
   * ( end auto-generated )
   *
   * @webref environment
   * @see PApplet#width
   * @see PApplet#size(int, int)
   */
  public int height = DEFAULT_HEIGHT;

  /**
   * ( begin auto-generated from pixelWidth.xml )
   *
   * When <b>pixelDensity(2)</d> is used to make use of a high resolution
   * display (called a Retina display on OS X or high-dpi on Windows and
   * Linux), the width and height of the sketch do not change, but the
   * number of pixels is doubled. As a result, all operations that use pixels
   * (like <b>loadPixels()</b>, <b>get()</b>, <b>set()</b>, etc.) happen
   * in this doubled space. As a convenience, the variables <b>pixelWidth</b>
   * and <b>pixelHeight<b> hold the actual width and height of the sketch
   * in pixels. This is useful for any sketch that uses the <b>pixels[]</b>
   * array, for instance, because the number of elements in the array will
   * be <b>pixelWidth*pixelHeight</b>, not <b>width*height</b>.
   *
   * ( end auto-generated )
   *
   * @webref environment
   * @see PApplet#pixelHeight
   * @see #pixelDensity(int)
   * @see #displayDensity()
   */
  public int pixelWidth;


  /**
   * ( begin auto-generated from pixelHeight.xml )
   *
   * When <b>pixelDensity(2)</d> is used to make use of a high resolution
   * display (called a Retina display on OS X or high-dpi on Windows and
   * Linux), the width and height of the sketch do not change, but the
   * number of pixels is doubled. As a result, all operations that use pixels
   * (like <b>loadPixels()</b>, <b>get()</b>, <b>set()</b>, etc.) happen
   * in this doubled space. As a convenience, the variables <b>pixelWidth</b>
   * and <b>pixelHeight<b> hold the actual width and height of the sketch
   * in pixels. This is useful for any sketch that uses the <b>pixels[]</b>
   * array, for instance, because the number of elements in the array will
   * be <b>pixelWidth*pixelHeight</b>, not <b>width*height</b>.
   *
   * ( end auto-generated )
   *
   * @webref environment
   * @see PApplet#pixelWidth
   * @see #pixelDensity(int)
   * @see #displayDensity()
   */
  public int pixelHeight;

  /**
   * Keeps track of ENABLE_KEY_REPEAT hint
   */
  protected boolean keyRepeatEnabled = false;

  /**
   * ( begin auto-generated from mouseX.xml )
   *
   * The system variable <b>mouseX</b> always contains the current horizontal
   * coordinate of the mouse.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   *
   *
   */
  public int mouseX;

  /**
   * ( begin auto-generated from mouseY.xml )
   *
   * The system variable <b>mouseY</b> always contains the current vertical
   * coordinate of the mouse.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   *
   */
  public int mouseY;

  /**
   * ( begin auto-generated from pmouseX.xml )
   *
   * The system variable <b>pmouseX</b> always contains the horizontal
   * position of the mouse in the frame previous to the current frame.<br />
   * <br />
   * You may find that <b>pmouseX</b> and <b>pmouseY</b> have different
   * values inside <b>draw()</b> and inside events like <b>mousePressed()</b>
   * and <b>mouseMoved()</b>. This is because they're used for different
   * roles, so don't mix them. Inside <b>draw()</b>, <b>pmouseX</b> and
   * <b>pmouseY</b> update only once per frame (once per trip through your
   * <b>draw()</b>). But, inside mouse events, they update each time the
   * event is called. If they weren't separated, then the mouse would be read
   * only once per frame, making response choppy. If the mouse variables were
   * always updated multiple times per frame, using <NOBR><b>line(pmouseX,
   * pmouseY, mouseX, mouseY)</b></NOBR> inside <b>draw()</b> would have lots
   * of gaps, because <b>pmouseX</b> may have changed several times in
   * between the calls to <b>line()</b>. Use <b>pmouseX</b> and
   * <b>pmouseY</b> inside <b>draw()</b> if you want values relative to the
   * previous frame. Use <b>pmouseX</b> and <b>pmouseY</b> inside the mouse
   * functions if you want continuous response.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int pmouseX;

  /**
   * ( begin auto-generated from pmouseY.xml )
   *
   * The system variable <b>pmouseY</b> always contains the vertical position
   * of the mouse in the frame previous to the current frame. More detailed
   * information about how <b>pmouseY</b> is updated inside of <b>draw()</b>
   * and mouse events is explained in the reference for <b>pmouseX</b>.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int pmouseY;

  /**
   * Previous mouseX/Y for the draw loop, separated out because this is
   * separate from the pmouseX/Y when inside the mouse event handlers.
   * See emouseX/Y for an explanation.
   */
  protected int dmouseX, dmouseY;

  /**
   * The pmouseX/Y for the event handlers (mousePressed(), mouseDragged() etc)
   * these are different because mouse events are queued to the end of
   * draw, so the previous position has to be updated on each event,
   * as opposed to the pmouseX/Y that's used inside draw, which is expected
   * to be updated once per trip through draw().
   */
  protected int emouseX, emouseY;

  /**
   * Used to set pmouseX/Y to mouseX/Y the first time mouseX/Y are used,
   * otherwise pmouseX/Y are always zero, causing a nasty jump.
   * <p>
   * Just using (frameCount == 0) won't work since mouseXxxxx()
   * may not be called until a couple frames into things.
   * <p>
   * @deprecated Please refrain from using this variable, it will be removed
   * from future releases of Processing because it cannot be used consistently
   * across platforms and input methods.
   */
  @Deprecated
  public boolean firstMouse = true;

  /**
   * ( begin auto-generated from mouseButton.xml )
   *
   * Processing automatically tracks if the mouse button is pressed and which
   * button is pressed. The value of the system variable <b>mouseButton</b>
   * is either <b>LEFT</b>, <b>RIGHT</b>, or <b>CENTER</b> depending on which
   * button is pressed.
   *
   * ( end auto-generated )
   *
   * <h3>Advanced:</h3>
   *
   * If running on Mac OS, a ctrl-click will be interpreted as the right-hand
   * mouse button (unlike Java, which reports it as the left mouse).
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public int mouseButton;

  /**
   * ( begin auto-generated from mousePressed_var.xml )
   *
   * Variable storing if a mouse button is pressed. The value of the system
   * variable <b>mousePressed</b> is true if a mouse button is pressed and
   * false if a button is not pressed.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public boolean mousePressed;


  /** @deprecated Use a mouse event handler that passes an event instead. */
  @Deprecated
  public MouseEvent mouseEvent;

  /**
   * ( begin auto-generated from key.xml )
   *
   * The system variable <b>key</b> always contains the value of the most
   * recent key on the keyboard that was used (either pressed or released).
   * <br/> <br/>
   * For non-ASCII keys, use the <b>keyCode</b> variable. The keys included
   * in the ASCII specification (BACKSPACE, TAB, ENTER, RETURN, ESC, and
   * DELETE) do not require checking to see if they key is coded, and you
   * should simply use the <b>key</b> variable instead of <b>keyCode</b> If
   * you're making cross-platform projects, note that the ENTER key is
   * commonly used on PCs and Unix and the RETURN key is used instead on
   * Macintosh. Check for both ENTER and RETURN to make sure your program
   * will work for all platforms.
   *
   * ( end auto-generated )
   *
   * <h3>Advanced</h3>
   *
   * Last key pressed.
   * <p>
   * If it's a coded key, i.e. UP/DOWN/CTRL/SHIFT/ALT,
   * this will be set to CODED (0xffff or 65535).
   *
   * @webref input:keyboard
   * @see PApplet#keyCode
   * @see PApplet#keyPressed
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public char key;

  /**
   * ( begin auto-generated from keyCode.xml )
   *
   * The variable <b>keyCode</b> is used to detect special keys such as the
   * UP, DOWN, LEFT, RIGHT arrow keys and ALT, CONTROL, SHIFT. When checking
   * for these keys, it's first necessary to check and see if the key is
   * coded. This is done with the conditional "if (key == CODED)" as shown in
   * the example.
   * <br/> <br/>
   * The keys included in the ASCII specification (BACKSPACE, TAB, ENTER,
   * RETURN, ESC, and DELETE) do not require checking to see if they key is
   * coded, and you should simply use the <b>key</b> variable instead of
   * <b>keyCode</b> If you're making cross-platform projects, note that the
   * ENTER key is commonly used on PCs and Unix and the RETURN key is used
   * instead on Macintosh. Check for both ENTER and RETURN to make sure your
   * program will work for all platforms.
   * <br/> <br/>
   * For users familiar with Java, the values for UP and DOWN are simply
   * shorter versions of Java's KeyEvent.VK_UP and KeyEvent.VK_DOWN. Other
   * keyCode values can be found in the Java <a
   * href="http://download.oracle.com/javase/6/docs/api/java/awt/event/KeyEvent.html">KeyEvent</a> reference.
   *
   * ( end auto-generated )
   *
   * <h3>Advanced</h3>
   * When "key" is set to CODED, this will contain a Java key code.
   * <p>
   * For the arrow keys, keyCode will be one of UP, DOWN, LEFT and RIGHT.
   * Also available are ALT, CONTROL and SHIFT. A full set of constants
   * can be obtained from java.awt.event.KeyEvent, from the VK_XXXX variables.
   *
   * @webref input:keyboard
   * @see PApplet#key
   * @see PApplet#keyPressed
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public int keyCode;

  /**
   * ( begin auto-generated from keyPressed_var.xml )
   *
   * The boolean system variable <b>keyPressed</b> is <b>true</b> if any key
   * is pressed and <b>false</b> if no keys are pressed.
   *
   * ( end auto-generated )
   * @webref input:keyboard
   * @see PApplet#key
   * @see PApplet#keyCode
   * @see PApplet#keyPressed()
   * @see PApplet#keyReleased()
   */
  public boolean keyPressed;
  List<Long> pressedKeys = new ArrayList<>(6);

  /**
   * The last KeyEvent object passed into a mouse function.
   * @deprecated Use a key event handler that passes an event instead.
   */
  @Deprecated
  public KeyEvent keyEvent;

  /**
   * ( begin auto-generated from focused.xml )
   *
   * Confirms if a Processing program is "focused", meaning that it is active
   * and will accept input from mouse or keyboard. This variable is "true" if
   * it is focused and "false" if not. This variable is often used when you
   * want to warn people they need to click on or roll over an applet before
   * it will work.
   *
   * ( end auto-generated )
   * @webref environment
   */
  public boolean focused = false;

//  /**
//   * Confirms if a Processing program is running inside a web browser. This
//   * variable is "true" if the program is online and "false" if not.
//   */
//  @Deprecated
//  public boolean online = false;
//  // This is deprecated because it's poorly named (and even more poorly
//  // understood). Further, we'll probably be removing applets soon, in which
//  // case this won't work at all. If you want this feature, you can check
//  // whether getAppletContext() returns null.

  /**
   * Time in milliseconds when the applet was started.
   * <p>
   * Used by the millis() function.
   */
  long millisOffset = System.currentTimeMillis();

  /**
   * ( begin auto-generated from frameRate_var.xml )
   *
   * The system variable <b>frameRate</b> contains the approximate frame rate
   * of the software as it executes. The initial value is 10 fps and is
   * updated with each frame. The value is averaged (integrated) over several
   * frames. As such, this value won't be valid until after 5-10 frames.
   *
   * ( end auto-generated )
   * @webref environment
   * @see PApplet#frameRate(float)
   * @see PApplet#frameCount
   */
  public float frameRate = 10;

  protected boolean looping = true;

  /** flag set to true when a redraw is asked for by the user */
  protected boolean redraw = true;

  /**
   * ( begin auto-generated from frameCount.xml )
   *
   * The system variable <b>frameCount</b> contains the number of frames
   * displayed since the program started. Inside <b>setup()</b> the value is
   * 0 and and after the first iteration of draw it is 1, etc.
   *
   * ( end auto-generated )
   * @webref environment
   * @see PApplet#frameRate(float)
   * @see PApplet#frameRate
   */
  public int frameCount;

  /** true if the sketch has stopped permanently. */
  public volatile boolean finished;

  /** used by the UncaughtExceptionHandler, so has to be static */
  static Throwable uncaughtThrowable;

  // public, but undocumented.. removing for 3.0a5
//  /**
//   * true if the animation thread is paused.
//   */
//  public volatile boolean paused;

  /**
   * true if exit() has been called so that things shut down
   * once the main thread kicks off.
   */
  protected boolean exitCalled;

  // messages to send if attached as an external vm

  /**
   * Position of the upper-lefthand corner of the editor window
   * that launched this applet.
   */
  static public final String ARGS_EDITOR_LOCATION = "--editor-location";

  static public final String ARGS_EXTERNAL = "--external";

  /**
   * Location for where to position the applet window on screen.
   * <p>
   * This is used by the editor to when saving the previous applet
   * location, or could be used by other classes to launch at a
   * specific position on-screen.
   */
  static public final String ARGS_LOCATION = "--location";

  /** Used by the PDE to suggest a display (set in prefs, passed on Run) */
  static public final String ARGS_DISPLAY = "--display";

//  static public final String ARGS_SPAN_DISPLAYS = "--span";

  static public final String ARGS_WINDOW_COLOR = "--window-color";

  static public final String ARGS_PRESENT = "--present";

  static public final String ARGS_STOP_COLOR = "--stop-color";

  static public final String ARGS_HIDE_STOP = "--hide-stop";

  /**
   * Allows the user or PdeEditor to set a specific sketch folder path.
   * <p>
   * Used by PdeEditor to pass in the location where saveFrame()
   * and all that stuff should write things.
   */
  static public final String ARGS_SKETCH_FOLDER = "--sketch-path";

  static public final String ARGS_DENSITY = "--density";

  /**
   * When run externally to a PdeEditor,
   * this is sent by the sketch when it quits.
   */
  static public final String EXTERNAL_STOP = "__STOP__";

  /**
   * When run externally to a PDE Editor, this is sent by the applet
   * whenever the window is moved.
   * <p>
   * This is used so that the editor can re-open the sketch window
   * in the same position as the user last left it.
   */
  static public final String EXTERNAL_MOVE = "__MOVE__";

  /** true if this sketch is being run by the PDE */
  boolean external = false;

  static final String ERROR_MIN_MAX =
    "Cannot use min() or max() on an empty array.";


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  protected PSurface surface;


  public PSurface getSurface() {
    return surface;
  }


  /**
   * A dummy frame to keep compatibility with 2.x code
   * and encourage users to update.
   */
  public Frame frame;


//  public Frame getFrame() {
//    return frame;
//  }
//
//
//  public void setFrame(Frame frame) {
//    this.frame = frame;
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


//  /**
//   * Applet initialization. This can do GUI work because the components have
//   * not been 'realized' yet: things aren't visible, displayed, etc.
//   */
//  public void init() {
////    println("init() called " + Integer.toHexString(hashCode()));
//    // using a local version here since the class variable is deprecated
////    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
////    screenWidth = screen.width;
////    screenHeight = screen.height;
//
//    defaultSize = true;
//    finished = false; // just for clarity
//
//    // this will be cleared by draw() if it is not overridden
//    looping = true;
//    redraw = true;  // draw this guy at least once
//    firstMouse = true;
//
//    // calculated dynamically on first call
////    // Removed in 2.1.2, brought back for 2.1.3. Usually sketchPath is set
////    // inside runSketch(), but if this sketch takes care of calls to init()
////    // when PApplet.main() is not used (i.e. it's in a Java application).
////    // THe path needs to be set here so that loadXxxx() functions work.
////    if (sketchPath == null) {
////      sketchPath = calcSketchPath();
////    }
//
//    // set during Surface.initFrame()
////    // Figure out the available display width and height.
////    // No major problem if this fails, we have to try again anyway in
////    // handleDraw() on the first (== 0) frame.
////    checkDisplaySize();
//
////    // Set the default size, until the user specifies otherwise
////    int w = sketchWidth();
////    int h = sketchHeight();
////    defaultSize = (w == DEFAULT_WIDTH) && (h == DEFAULT_HEIGHT);
////
////    g = makeGraphics(w, h, sketchRenderer(), null, true);
////    // Fire component resize event
////    setSize(w, h);
////    setPreferredSize(new Dimension(w, h));
////
////    width = g.width;
////    height = g.height;
//
//    surface.startThread();
//  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  boolean insideSettings;

  String renderer = JAVA2D;
//  int quality = 2;
  int smooth = 1;  // default smoothing (whatever that means for the renderer)

  boolean fullScreen;
  int display = -1;  // use default
  GraphicsDevice[] displayDevices;
  // Unlike the others above, needs to be public to support
  // the pixelWidth and pixelHeight fields.
  public int pixelDensity = 1;
  int suggestedDensity = -1;

  boolean present;

  String outputPath;
  OutputStream outputStream;

  // Background default needs to be different from the default value in
  // PGraphics.backgroundColor, otherwise size(100, 100) bg spills over.
  // https://github.com/processing/processing/issues/2297
  int windowColor = 0xffDDDDDD;


  /**
   * @param method "size" or "fullScreen"
   * @param args parameters passed to the function so we can show the user
   * @return true if safely inside the settings() method
   */
  boolean insideSettings(String method, Object... args) {
    if (insideSettings) {
      return true;
    }
    final String url = "https://processing.org/reference/" + method + "_.html";
    if (!external) {  // post a warning for users of Eclipse and other IDEs
      StringList argList = new StringList(args);
      System.err.println("When not using the PDE, " + method + "() can only be used inside settings().");
      System.err.println("Remove the " + method + "() method from setup(), and add the following:");
      System.err.println("public void settings() {");
      System.err.println("  " + method + "(" + argList.join(", ") + ");");
      System.err.println("}");
    }
    throw new IllegalStateException(method + "() cannot be used here, see " + url);
  }


  void handleSettings() {
    insideSettings = true;

    // Need the list of display devices to be queried already for usage below.
    // https://github.com/processing/processing/issues/3295
    // https://github.com/processing/processing/issues/3296
    // Not doing this from a static initializer because it may cause
    // PApplet to cache and the values to stick through subsequent runs.
    // Instead make it a runtime thing and a local variable.
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
    GraphicsDevice device = ge.getDefaultScreenDevice();
    displayDevices = ge.getScreenDevices();

    // Default or unparsed will be -1, spanning will be 0, actual displays will
    // be numbered from 1 because it's too weird to say "display 0" in prefs.
    if (display > 0 && display <= displayDevices.length) {
      device = displayDevices[display-1];
    }
    // Set displayWidth and displayHeight for people still using those.
    DisplayMode displayMode = device.getDisplayMode();
    displayWidth = displayMode.getWidth();
    displayHeight = displayMode.getHeight();

    // Here's where size(), fullScreen(), smooth(N) and noSmooth() might
    // be called, conjuring up the demons of various rendering configurations.
    settings();

    if (display == SPAN && platform == MACOSX) {
      // Make sure "Displays have separate Spaces" is unchecked
      // in System Preferences > Mission Control
      Process p = exec("defaults", "read", "com.apple.spaces", "spans-displays");
      BufferedReader outReader = createReader(p.getInputStream());
      BufferedReader errReader = createReader(p.getErrorStream());
      StringBuilder stdout = new StringBuilder();
      StringBuilder stderr = new StringBuilder();
      String line = null;
      try {
        while ((line = outReader.readLine()) != null) {
          stdout.append(line);
        }
        while ((line = errReader.readLine()) != null) {
          stderr.append(line);
        }
      } catch (IOException e) {
        printStackTrace(e);
      }

      int resultCode = -1;
      try {
        resultCode = p.waitFor();
      } catch (InterruptedException e) { }

      String result = trim(stdout.toString());
      if ("0".equals(result)) {
        EventQueue.invokeLater(new Runnable() {
          public void run() {
            checkLookAndFeel();
            final String msg =
              "To use fullScreen(SPAN), first turn off “Displays have separate spaces”\n" +
              "in System Preferences \u2192 Mission Control. Then log out and log back in.";
            JOptionPane.showMessageDialog(null, msg, "Apple's Defaults Stink",
                                          JOptionPane.WARNING_MESSAGE);
          }
        });
      } else if (!"1".equals(result)) {
        System.err.println("Could not check the status of “Displays have separate spaces.”");
        System.err.format("Received message '%s' and result code %d.%n", trim(stderr.toString()), resultCode);
      }
    }

    insideSettings = false;
  }


  /**
  * ( begin auto-generated from settings.xml )
  *
  * Description to come...
  *
  * ( end auto-generated )
  *
  * Override this method to call size() when not using the PDE.
  *
  * @webref environment
  * @see PApplet#fullScreen()
  * @see PApplet#setup()
  * @see PApplet#size(int,int)
  * @see PApplet#smooth()
  */
  public void settings() {
    // is this necessary? (doesn't appear to be, so removing)
    //size(DEFAULT_WIDTH, DEFAULT_HEIGHT, JAVA2D);
  }


  final public int sketchWidth() {
    return width;
  }


  final public int sketchHeight() {
    return height;
  }


  final public String sketchRenderer() {
    return renderer;
  }


  // Named quality instead of smooth to avoid people trying to set (or get)
  // the current smooth level this way. Also that smooth(number) isn't really
  // public or well-known API. It's specific to the capabilities of the
  // rendering surface, and somewhat independent of whether the sketch is
  // smoothing at any given time. It's also a bit like getFill() would return
  // true/false for whether fill was enabled, getFillColor() would return the
  // color itself. Or at least that's what I can recall at the moment. [fry]
//  public int sketchQuality() {
//    //return 2;
//    return quality;
//  }
  // smoothing 1 is default.. 0 is none.. 2,4,8 depend on renderer
  final public int sketchSmooth() {
    return smooth;
  }


  final public boolean sketchFullScreen() {
    //return false;
    return fullScreen;
  }


//  // Could be named 'screen' instead of display since it's the people using
//  // full screen who will be looking for it. On the other hand, screenX/Y/Z
//  // makes things confusing, and if 'displayIndex' exists...
//  public boolean sketchSpanDisplays() {
//    //return false;
//    return spanDisplays;
//  }


  // Numbered from 1, SPAN (0) means all displays, -1 means the default display
  final public int sketchDisplay() {
    return display;
  }


  final public String sketchOutputPath() {
    //return null;
    return outputPath;
  }


  final public OutputStream sketchOutputStream() {
    //return null;
    return outputStream;
  }


  final public int sketchWindowColor() {
    return windowColor;
  }


  final public int sketchPixelDensity() {
    return pixelDensity;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

 /**
  * ( begin auto-generated from displayDensity.xml )
  *
  * This function returns the number "2" if the screen is a high-density
  * screen (called a Retina display on OS X or high-dpi on Windows and Linux)
  * and a "1" if not. This information is useful for a program to adapt to
  * run at double the pixel density on a screen that supports it.
  *
  * ( end auto-generated )
  *
  * @webref environment
  * @see PApplet#pixelDensity(int)
  * @see PApplet#size(int,int)
  */
  public int displayDensity() {
    if (display != SPAN && (fullScreen || present)) {
      return displayDensity(display);
    }
    // walk through all displays, use 2 if any display is 2
    for (int i = 0; i < displayDevices.length; i++) {
      if (displayDensity(i+1) == 2) {
        return 2;
      }
    }
    // If nobody's density is 2 then everyone is 1
    return 1;
  }

 /**
  * @param display the display number to check
  */
  public int displayDensity(int display) {
    if (PApplet.platform == PConstants.MACOSX) {
      // This should probably be reset each time there's a display change.
      // A 5-minute search didn't turn up any such event in the Java 7 API.
      // Also, should we use the Toolkit associated with the editor window?
      final String javaVendor = System.getProperty("java.vendor");
      if (javaVendor.contains("Oracle")) {
        GraphicsDevice device;
        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();

        if (display == -1) {
          device = env.getDefaultScreenDevice();

        } else if (display == SPAN) {
          throw new RuntimeException("displayDensity() only works with specific display numbers");

        } else {
          GraphicsDevice[] devices = env.getScreenDevices();
          if (display > 0 && display <= devices.length) {
            device = devices[display - 1];
          } else {
            if (devices.length == 1) {
              System.err.println("Only one display is currently known, use displayDensity(1).");
            } else {
              System.err.format("Your displays are numbered %d through %d, " +
                "pass one of those numbers to displayDensity()%n", 1, devices.length);
            }
            throw new RuntimeException("Display " + display + " does not exist.");
          }
        }

        try {
          Field field = device.getClass().getDeclaredField("scale");
          if (field != null) {
            field.setAccessible(true);
            Object scale = field.get(device);

            if (scale instanceof Integer && ((Integer)scale).intValue() == 2) {
              return 2;
            }
          }
        } catch (Exception ignore) { }
      }
    } else if (PApplet.platform == PConstants.WINDOWS ||
        PApplet.platform == PConstants.LINUX) {
      if (suggestedDensity == -1) {
        // TODO: detect and return DPI scaling using JNA; Windows has
        //   a system-wide value, not sure how it works on Linux
        return 1;
      } else if (suggestedDensity == 1 || suggestedDensity == 2) {
        return suggestedDensity;
      }
    }
    return 1;
  }


 /**
  * @webref environment
  * @param density 1 or 2
  *
  */
  public void pixelDensity(int density) {
    //println(density + " " + this.pixelDensity);
    if (density != this.pixelDensity) {
      if (insideSettings("pixelDensity", density)) {
        if (density != 1 && density != 2) {
          throw new RuntimeException("pixelDensity() can only be 1 or 2");
        }
        if (!FX2D.equals(renderer) && density == 2 && displayDensity() == 1) {
          // FX has its own check in PSurfaceFX
          // Don't throw exception because the sketch should still work
          System.err.println("pixelDensity(2) is not available for this display");
          this.pixelDensity = 1;
        } else {
          this.pixelDensity = density;
        }
      } else {
        System.err.println("not inside settings");
        // this should only be reachable when not running in the PDE,
        // so saying it's a settings()--not just setup()--issue should be ok
        throw new RuntimeException("pixelDensity() can only be used inside settings()");
      }
    }
  }


  /**
   * Called by PSurface objects to set the width and height variables,
   * and update the pixelWidth and pixelHeight variables.
   */
  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
    pixelWidth = width * pixelDensity;
    pixelHeight = height * pixelDensity;
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  /**
   * @nowebref
   */
  public void smooth() {
    smooth(1);
  }

  /**
   * @webref environment
   * @param level either 2, 3, 4, or 8 depending on the renderer
   */
  public void smooth(int level) {
    if (insideSettings) {
      this.smooth = level;

    } else if (this.smooth != level) {
      smoothWarning("smooth");
    }
  }

  /**
   * @webref environment
   */
  public void noSmooth() {
    if (insideSettings) {
      this.smooth = 0;

    } else if (this.smooth != 0) {
      smoothWarning("noSmooth");
    }
  }


  private void smoothWarning(String method) {
    // When running from the PDE, say setup(), otherwise say settings()
    final String where = external ? "setup" : "settings";
    PGraphics.showWarning("%s() can only be used inside %s()", method, where);
    if (external) {
      PGraphics.showWarning("When run from the PDE, %s() is automatically moved from setup() to settings()", method);
    }
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


  public PGraphics getGraphics() {
    return g;
  }


  // TODO should this join the sketchXxxx() functions specific to settings()?
  public void orientation(int which) {
    // ignore calls to the orientation command
  }


  /**
   * Called by the browser or applet viewer to inform this applet that it
   * should start its execution. It is called after the init method and
   * each time the applet is revisited in a Web page.
   * <p/>
   * Called explicitly via the first call to PApplet.paint(), because
   * PAppletGL needs to have a usable screen before getting things rolling.
   */
  public void start() {
//    paused = false; // unpause the thread  // removing for 3.0a5, don't think we want this here

    resume();
    handleMethods("resume");
    surface.resumeThread();
  }


  /**
   * Called by the browser or applet viewer to inform
   * this applet that it should stop its execution.
   * <p/>
   * Unfortunately, there are no guarantees from the Java spec
   * when or if stop() will be called (i.e. on browser quit,
   * or when moving between web pages), and it's not always called.
   */
  public void stop() {
    // this used to shut down the sketch, but that code has
    // been moved to destroy/dispose()

//    if (paused) {
//      synchronized (pauseObject) {
//      try {
//          pauseObject.wait();
//        } catch (InterruptedException e) {
//          // waiting for this interrupt on a start() (resume) call
//        }
//      }
//    }

    //paused = true; // causes animation thread to sleep  // 3.0a5
    pause();
    handleMethods("pause");
    // calling this down here, since it's another thread it's safer to call
    // pause() and the registered pause methods first.
    surface.pauseThread();

    // actual pause will happen in the run() method

//    synchronized (pauseObject) {
//      debug("stop() calling pauseObject.wait()");
//      try {
//        pauseObject.wait();
//      } catch (InterruptedException e) {
//        // waiting for this interrupt on a start() (resume) call
//      }
//    }
  }


  /**
   * Sketch has been paused. Called when switching tabs in a browser or
   * swapping to a different application on Android. Also called just before
   * quitting. Use to safely disable things like serial, sound, or sensors.
   */
  public void pause() { }


  /**
   * Sketch has resumed. Called when switching tabs in a browser or
   * swapping to this application on Android. Also called on startup.
   * Use this to safely disable things like serial, sound, or sensors.
   */
  public void resume() { }


//  /**
//   * Called by the browser or applet viewer to inform this applet
//   * that it is being reclaimed and that it should destroy
//   * any resources that it has allocated.
//   * <p/>
//   * destroy() supposedly gets called as the applet viewer
//   * is shutting down the applet. stop() is called
//   * first, and then destroy() to really get rid of things.
//   * no guarantees on when they're run (on browser quit, or
//   * when moving between pages), though.
//   */
//  @Override
//  public void destroy() {
//    this.dispose();
//  }


  //////////////////////////////////////////////////////////////


  /** Map of registered methods, stored by name. */
  HashMap<String, RegisteredMethods> registerMap =
    new HashMap<>();

  /** Lock when un/registering from multiple threads */
  private final Object registerLock = new Object[0];


  class RegisteredMethods {
    int count;
    Object[] objects;
    // Because the Method comes from the class being called,
    // it will be unique for most, if not all, objects.
    Method[] methods;
    Object[] emptyArgs = new Object[] { };


    void handle() {
      handle(emptyArgs);
    }


    void handle(Object[] args) {
      for (int i = 0; i < count; i++) {
        try {
          methods[i].invoke(objects[i], args);
        } catch (Exception e) {
          // check for wrapped exception, get root exception
          Throwable t;
          if (e instanceof InvocationTargetException) {
            InvocationTargetException ite = (InvocationTargetException) e;
            t = ite.getCause();
          } else {
            t = e;
          }
          // check for RuntimeException, and allow to bubble up
          if (t instanceof RuntimeException) {
            // re-throw exception
            throw (RuntimeException) t;
          } else {
            // trap and print as usual
            printStackTrace(t);
          }
        }
      }
    }


    void add(Object object, Method method) {
      if (findIndex(object) == -1) {
        if (objects == null) {
          objects = new Object[5];
          methods = new Method[5];

        } else if (count == objects.length) {
          objects = (Object[]) PApplet.expand(objects);
          methods = (Method[]) PApplet.expand(methods);
        }
        objects[count] = object;
        methods[count] = method;
        count++;
      } else {
        die(method.getName() + "() already added for this instance of " +
            object.getClass().getName());
      }
    }


    /**
     * Removes first object/method pair matched (and only the first,
     * must be called multiple times if object is registered multiple times).
     * Does not shrink array afterwards, silently returns if method not found.
     */
//    public void remove(Object object, Method method) {
//      int index = findIndex(object, method);
    public void remove(Object object) {
      int index = findIndex(object);
      if (index != -1) {
        // shift remaining methods by one to preserve ordering
        count--;
        for (int i = index; i < count; i++) {
          objects[i] = objects[i+1];
          methods[i] = methods[i+1];
        }
        // clean things out for the gc's sake
        objects[count] = null;
        methods[count] = null;
      }
    }


//    protected int findIndex(Object object, Method method) {
    protected int findIndex(Object object) {
      for (int i = 0; i < count; i++) {
        if (objects[i] == object) {
//        if (objects[i] == object && methods[i].equals(method)) {
          //objects[i].equals() might be overridden, so use == for safety
          // since here we do care about actual object identity
          //methods[i]==method is never true even for same method, so must use
          // equals(), this should be safe because of object identity
          return i;
        }
      }
      return -1;
    }
  }


  /**
   * Register a built-in event so that it can be fired for libraries, etc.
   * Supported events include:
   * <ul>
   * <li>pre – at the very top of the draw() method (safe to draw)
   * <li>draw – at the end of the draw() method (safe to draw)
   * <li>post – after draw() has exited (not safe to draw)
   * <li>pause – called when the sketch is paused
   * <li>resume – called when the sketch is resumed
   * <li>dispose – when the sketch is shutting down (definitely not safe to draw)
   * <ul>
   * In addition, the new (for 2.0) processing.event classes are passed to
   * the following event types:
   * <ul>
   * <li>mouseEvent
   * <li>keyEvent
   * <li>touchEvent
   * </ul>
   * The older java.awt events are no longer supported.
   * See the Library Wiki page for more details.
   * @param methodName name of the method to be called
   * @param target the target object that should receive the event
   */
  public void registerMethod(String methodName, Object target) {
    if (methodName.equals("mouseEvent")) {
      registerWithArgs("mouseEvent", target, new Class[] { processing.event.MouseEvent.class });

    } else if (methodName.equals("keyEvent")) {
      registerWithArgs("keyEvent", target, new Class[] { processing.event.KeyEvent.class });

    } else if (methodName.equals("touchEvent")) {
      registerWithArgs("touchEvent", target, new Class[] { processing.event.TouchEvent.class });

    } else {
      registerNoArgs(methodName, target);
    }
  }


  private void registerNoArgs(String name, Object o) {
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name);
      synchronized (registerLock) {
        RegisteredMethods meth = registerMap.get(name);
        if (meth == null) {
          meth = new RegisteredMethods();
          registerMap.put(name, meth);
        }
        meth.add(o, method);
      }
    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


  private void registerWithArgs(String name, Object o, Class<?> cargs[]) {
    Class<?> c = o.getClass();
    try {
      Method method = c.getMethod(name, cargs);
      synchronized (registerLock) {
        RegisteredMethods meth = registerMap.get(name);
        if (meth == null) {
          meth = new RegisteredMethods();
          registerMap.put(name, meth);
        }
        meth.add(o, method);
      }
    } catch (NoSuchMethodException nsme) {
      die("There is no public " + name + "() method in the class " +
          o.getClass().getName());

    } catch (Exception e) {
      die("Could not register " + name + " + () for " + o, e);
    }
  }


//  public void registerMethod(String methodName, Object target, Object... args) {
//    registerWithArgs(methodName, target, args);
//  }


  public void unregisterMethod(String name, Object target) {
    synchronized (registerLock) {
      RegisteredMethods meth = registerMap.get(name);
      if (meth == null) {
        die("No registered methods with the name " + name + "() were found.");
      }
      try {
//      Method method = o.getClass().getMethod(name, new Class[] {});
//      meth.remove(o, method);
        meth.remove(target);
      } catch (Exception e) {
        die("Could not unregister " + name + "() for " + target, e);
      }
    }
  }


  protected void handleMethods(String methodName) {
    synchronized (registerLock) {
      RegisteredMethods meth = registerMap.get(methodName);
      if (meth != null) {
        meth.handle();
      }
    }
  }


  protected void handleMethods(String methodName, Object[] args) {
    synchronized (registerLock) {
      RegisteredMethods meth = registerMap.get(methodName);
      if (meth != null) {
        meth.handle(args);
      }
    }
  }


  /*
  @Deprecated
  public void registerSize(Object o) {
    System.err.println("The registerSize() command is no longer supported.");
//    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    registerWithArgs(sizeMethods, "size", o, methodArgs);
  }


  @Deprecated
  public void registerPre(Object o) {
    registerNoArgs("pre", o);
  }


  @Deprecated
  public void registerDraw(Object o) {
    registerNoArgs("draw", o);
  }


  @Deprecated
  public void registerPost(Object o) {
    registerNoArgs("post", o);
  }


  @Deprecated
  public void registerDispose(Object o) {
    registerNoArgs("dispose", o);
  }


  @Deprecated
  public void unregisterSize(Object o) {
    System.err.println("The unregisterSize() command is no longer supported.");
//    Class<?> methodArgs[] = new Class[] { Integer.TYPE, Integer.TYPE };
//    unregisterWithArgs(sizeMethods, "size", o, methodArgs);
  }


  @Deprecated
  public void unregisterPre(Object o) {
    unregisterMethod("pre", o);
  }


  @Deprecated
  public void unregisterDraw(Object o) {
    unregisterMethod("draw", o);
  }


  @Deprecated
  public void unregisterPost(Object o) {
    unregisterMethod("post", o);
  }


  @Deprecated
  public void unregisterDispose(Object o) {
    unregisterMethod("dispose", o);
  }


  // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .

  // Old methods with AWT API that should not be used.
  // These were never implemented on Android so they're stored separately.

  RegisteredMethods mouseEventMethods, keyEventMethods;


  protected void reportDeprecation(Class<?> c, boolean mouse) {
    if (g != null) {
      PGraphics.showWarning("The class " + c.getName() +
                            " is incompatible with Processing 2.0.");
      PGraphics.showWarning("A library (or other code) is using register" +
                            (mouse ? "Mouse" : "Key") + "Event() " +
                            "which is no longer available.");
      // This will crash with OpenGL, so quit anyway
      if (g instanceof PGraphicsOpenGL) {
        PGraphics.showWarning("Stopping the sketch because this code will " +
                                          "not work correctly with OpenGL.");
        throw new RuntimeException("This sketch uses a library that " +
                                               "needs to be updated for Processing 2.0.");
      }
    }
  }


  @Deprecated
  public void registerMouseEvent(Object o) {
    Class<?> c = o.getClass();
    reportDeprecation(c, true);
    try {
      Method method = c.getMethod("mouseEvent", new Class[] { java.awt.event.MouseEvent.class });
      if (mouseEventMethods == null) {
        mouseEventMethods = new RegisteredMethods();
      }
      mouseEventMethods.add(o, method);
    } catch (Exception e) {
      die("Could not register mouseEvent() for " + o, e);
    }
  }


  @Deprecated
  public void unregisterMouseEvent(Object o) {
    try {
//      Method method = o.getClass().getMethod("mouseEvent", new Class[] { MouseEvent.class });
//      mouseEventMethods.remove(o, method);
      mouseEventMethods.remove(o);
    } catch (Exception e) {
      die("Could not unregister mouseEvent() for " + o, e);
    }
  }


  @Deprecated
  public void registerKeyEvent(Object o) {
    Class<?> c = o.getClass();
    reportDeprecation(c, false);
    try {
      Method method = c.getMethod("keyEvent", new Class[] { java.awt.event.KeyEvent.class });
      if (keyEventMethods == null) {
        keyEventMethods = new RegisteredMethods();
      }
      keyEventMethods.add(o, method);
    } catch (Exception e) {
      die("Could not register keyEvent() for " + o, e);
    }
  }


  @Deprecated
  public void unregisterKeyEvent(Object o) {
    try {
//      Method method = o.getClass().getMethod("keyEvent", new Class[] { KeyEvent.class });
//      keyEventMethods.remove(o, method);
      keyEventMethods.remove(o);
    } catch (Exception e) {
      die("Could not unregister keyEvent() for " + o, e);
    }
  }
  */



  //////////////////////////////////////////////////////////////

/**
   * ( begin auto-generated from setup.xml )
   *
   * The <b>setup()</b> function is called once when the program starts. It's
   * used to define initial
   * enviroment properties such as screen size and background color and to
   * load media such as images
   * and fonts as the program starts. There can only be one <b>setup()</b>
   * function for each program and
   * it shouldn't be called again after its initial execution. Note:
   * Variables declared within
   * <b>setup()</b> are not accessible within other functions, including
   * <b>draw()</b>.
   *
   * ( end auto-generated )
 * @webref structure
 * @usage web_application
 * @see PApplet#size(int, int)
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#draw()
 */
  public void setup() {
  }

/**
   * ( begin auto-generated from draw.xml )
   *
   * Called directly after <b>setup()</b> and continuously executes the lines
   * of code contained inside its block until the program is stopped or
   * <b>noLoop()</b> is called. The <b>draw()</b> function is called
   * automatically and should never be called explicitly. It should always be
   * controlled with <b>noLoop()</b>, <b>redraw()</b> and <b>loop()</b>.
   * After <b>noLoop()</b> stops the code in <b>draw()</b> from executing,
   * <b>redraw()</b> causes the code inside <b>draw()</b> to execute once and
   * <b>loop()</b> will causes the code inside <b>draw()</b> to execute
   * continuously again. The number of times <b>draw()</b> executes in each
   * second may be controlled with <b>frameRate()</b> function.
   * There can only be one <b>draw()</b> function for each sketch
   * and <b>draw()</b> must exist if you want the code to run continuously or
   * to process events such as <b>mousePressed()</b>. Sometimes, you might
   * have an empty call to <b>draw()</b> in your program as shown in the
   * above example.
   *
   * ( end auto-generated )
 * @webref structure
 * @usage web_application
 * @see PApplet#setup()
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#redraw()
 * @see PApplet#frameRate(float)
 * @see PGraphics#background(float, float, float, float)
 */
  public void draw() {
    // if no draw method, then shut things down
    //System.out.println("no draw method, goodbye");
    finished = true;
  }


  //////////////////////////////////////////////////////////////


  /*
  protected void resizeRenderer(int newWidth, int newHeight) {
    debug("resizeRenderer request for " + newWidth + " " + newHeight);
    if (width != newWidth || height != newHeight) {
      debug("  former size was " + width + " " + height);
      g.setSize(newWidth, newHeight);
      width = newWidth;
      height = newHeight;
    }
  }
  */


  /**
   * Create a full-screen sketch using the default renderer.
   */
  public void fullScreen() {
    if (!fullScreen) {
      if (insideSettings("fullScreen")) {
        this.fullScreen = true;
      }
    }
  }


  public void fullScreen(int display) {
    if (!fullScreen || display != this.display) {
      if (insideSettings("fullScreen", display)) {
        this.fullScreen = true;
        this.display = display;
      }
    }
  }


/**
  * ( begin auto-generated from fullScreen.xml )
  *
  * Description to come...
  *
  * ( end auto-generated )
  * @webref environment
  * @param renderer the renderer to use, e.g. P2D, P3D, JAVA2D (default)
  * @see PApplet#settings()
  * @see PApplet#setup()
  * @see PApplet#size(int,int)
  * @see PApplet#smooth()
  */
  public void fullScreen(String renderer) {
    if (!fullScreen ||
        !renderer.equals(this.renderer)) {
      if (insideSettings("fullScreen", renderer)) {
        this.fullScreen = true;
        this.renderer = renderer;
      }
    }
  }


  /**
   * @param display the screen to run the sketch on (1, 2, 3, etc. or on multiple screens using SPAN)
   */

  public void fullScreen(String renderer, int display) {
    if (!fullScreen ||
        !renderer.equals(this.renderer) ||
        display != this.display) {
      if (insideSettings("fullScreen", renderer, display)) {
        this.fullScreen = true;
        this.renderer = renderer;
        this.display = display;
      }
    }
  }


  /**
   * ( begin auto-generated from size.xml )
   *
   * Defines the dimension of the display window in units of pixels. The
   * <b>size()</b> function must be the first line in <b>setup()</b>. If
   * <b>size()</b> is not used, the default size of the window is 100x100
   * pixels. The system variables <b>width</b> and <b>height</b> are set by
   * the parameters passed to this function.<br />
   * <br />
   * Do not use variables as the parameters to <b>size()</b> function,
   * because it will cause problems when exporting your sketch. When
   * variables are used, the dimensions of your sketch cannot be determined
   * during export. Instead, employ numeric values in the <b>size()</b>
   * statement, and then use the built-in <b>width</b> and <b>height</b>
   * variables inside your program when the dimensions of the display window
   * are needed.<br />
   * <br />
   * The <b>size()</b> function can only be used once inside a sketch, and
   * cannot be used for resizing.<br/>
   * <br/> <b>renderer</b> parameter selects which rendering engine to use.
   * For example, if you will be drawing 3D shapes, use <b>P3D</b>, if you
   * want to export images from a program as a PDF file use <b>PDF</b>. A
   * brief description of the three primary renderers follows:<br />
   * <br />
   * <b>P2D</b> (Processing 2D) - The default renderer that supports two
   * dimensional drawing.<br />
   * <br />
   * <b>P3D</b> (Processing 3D) - 3D graphics renderer that makes use of
   * OpenGL-compatible graphics hardware.<br />
   * <br />
   * <b>PDF</b> - The PDF renderer draws 2D graphics directly to an Acrobat
   * PDF file. This produces excellent results when you need vector shapes
   * for high resolution output or printing. You must first use Import
   * Library &rarr; PDF to make use of the library. More information can be
   * found in the PDF library reference.<br />
   * <br />
   * The P3D renderer doesn't support <b>strokeCap()</b> or
   * <b>strokeJoin()</b>, which can lead to ugly results when using
   * <b>strokeWeight()</b>. (<a
   * href="http://code.google.com/p/processing/issues/detail?id=123">Issue
   * 123</a>) <br />
   * <br />
   * The maximum width and height is limited by your operating system, and is
   * usually the width and height of your actual screen. On some machines it
   * may simply be the number of pixels on your current screen, meaning that
   * a screen of 800x600 could support <b>size(1600, 300)</b>, since it's the
   * same number of pixels. This varies widely so you'll have to try
   * different rendering modes and sizes until you get what you're looking
   * for. If you need something larger, use <b>createGraphics</b> to create a
   * non-visible drawing surface.<br />
   * <br />
   * Again, the <b>size()</b> function must be the first line of the code (or
   * first item inside setup). Any code that appears before the <b>size()</b>
   * command may run more than once, which can lead to confusing results.
   *
   * ( end auto-generated )
   *
   * <h3>Advanced</h3>
   * If using Java 1.3 or later, this will default to using
   * PGraphics2, the Java2D-based renderer. If using Java 1.1,
   * or if PGraphics2 is not available, then PGraphics will be used.
   * To set your own renderer, use the other version of the size()
   * method that takes a renderer as its last parameter.
   * <p>
   * If called once a renderer has already been set, this will
   * use the previous renderer and simply resize it.
   *
   * @webref environment
   * @param width width of the display window in units of pixels
   * @param height height of the display window in units of pixels
   * @see PApplet#width
   * @see PApplet#height
   */
  public void size(int width, int height) {
    // Check to make sure the width/height have actually changed. It's ok to
    // have size() duplicated (and may be better to not remove it from where
    // it sits in the code anyway when adding it to settings()). Only take
    // action if things have changed.
    if (width != this.width ||
        height != this.height) {
      if (insideSettings("size", width, height)) {
        this.width = width;
        this.height = height;
      }
    }
  }


  public void size(int width, int height, String renderer) {
    if (width != this.width ||
        height != this.height ||
        !renderer.equals(this.renderer)) {
      //println(width, height, renderer, this.width, this.height, this.renderer);
      if (insideSettings("size", width, height, "\"" + renderer + "\"")) {
        this.width = width;
        this.height = height;
        this.renderer = renderer;
      }
    }
  }


  /**
   * @nowebref
   */
  public void size(int width, int height, String renderer, String path) {
    // Don't bother checking path, it's probably been modified to absolute,
    // so it would always trigger. But the alternative is comparing the
    // canonical file, which seems overboard.
    if (width != this.width ||
        height != this.height ||
        !renderer.equals(this.renderer)) {
      if (insideSettings("size", width, height, "\"" + renderer + "\"",
                         "\"" + path + "\"")) {
        this.width = width;
        this.height = height;
        this.renderer = renderer;
        this.outputPath = path;
      }
    }

    /*
    if (!renderer.equals(sketchRenderer())) {
      if (external) {
        // The PDE should have parsed it, but something still went wrong
        final String msg =
          String.format("Something bad happened when calling " +
                        "size(%d, %d, %s, %s)", w, h, renderer, path);
        throw new RuntimeException(msg);

      } else {
        System.err.println("Because you're not running from the PDE, add this to your code:");
        System.err.println("public String sketchRenderer() {");
        System.err.println("  return \"" + renderer + "\";");
        System.err.println("}");
        throw new RuntimeException("The sketchRenderer() method is not implemented.");
      }
    }
    */

    // size() shouldn't actually do anything here [3.0a8]
//    surface.setSize(w, h);
    // this won't be absolute, which will piss off PDF [3.0a8]
//    g.setPath(path);  // finally, a path

//    // Run this from the EDT, just cuz it's AWT stuff (or maybe later Swing)
//   EventQueue.invokeLater(new Runnable() {
//     public void run() {
//    // Set the preferred size so that the layout managers can handle it
//    setPreferredSize(new Dimension(w, h));
//    setSize(w, h);
//     }
//   });
//
//    // ensure that this is an absolute path
//    if (path != null) path = savePath(path);
//
//    String currentRenderer = g.getClass().getName();
//    if (currentRenderer.equals(renderer)) {
////      // Avoid infinite loop of throwing exception to reset renderer
////      resizeRenderer(w, h);
//      surface.setSize(w, h);
//
//    } else {  // renderer change attempted
//      // no longer kosher with 3.0a5
//      throw new RuntimeException("Y'all need to implement sketchRenderer()");
//      /*
//      // otherwise ok to fall through and create renderer below
//      // the renderer is changing, so need to create a new object
//      g = makeGraphics(w, h, renderer, path, true);
//      this.width = w;
//      this.height = h;
//
//      // fire resize event to make sure the applet is the proper size
////      setSize(iwidth, iheight);
//      // this is the function that will run if the user does their own
//      // size() command inside setup, so set defaultSize to false.
//      defaultSize = false;
//
//      // throw an exception so that setup() is called again
//      // but with a properly sized render
//      // this is for opengl, which needs a valid, properly sized
//      // display before calling anything inside setup().
//      throw new RendererChangeException();
//      */
//    }
  }


  public PGraphics createGraphics(int w, int h) {
    return createGraphics(w, h, JAVA2D);
  }


  /**
   * ( begin auto-generated from createGraphics.xml )
   *
   * Creates and returns a new <b>PGraphics</b> object of the types P2D or
   * P3D. Use this class if you need to draw into an off-screen graphics
   * buffer. The PDF renderer requires the filename parameter. The DXF
   * renderer should not be used with <b>createGraphics()</b>, it's only
   * built for use with <b>beginRaw()</b> and <b>endRaw()</b>.<br />
   * <br />
   * It's important to call any drawing functions between <b>beginDraw()</b>
   * and <b>endDraw()</b> statements. This is also true for any functions
   * that affect drawing, such as <b>smooth()</b> or <b>colorMode()</b>.<br/>
   * <br/> the main drawing surface which is completely opaque, surfaces
   * created with <b>createGraphics()</b> can have transparency. This makes
   * it possible to draw into a graphics and maintain the alpha channel. By
   * using <b>save()</b> to write a PNG or TGA file, the transparency of the
   * graphics object will be honored. Note that transparency levels are
   * binary: pixels are either complete opaque or transparent. For the time
   * being, this means that text characters will be opaque blocks. This will
   * be fixed in a future release (<a
   * href="http://code.google.com/p/processing/issues/detail?id=80">Issue 80</a>).
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   * Create an offscreen PGraphics object for drawing. This can be used
   * for bitmap or vector images drawing or rendering.
   * <UL>
   * <LI>Do not use "new PGraphicsXxxx()", use this method. This method
   * ensures that internal variables are set up properly that tie the
   * new graphics context back to its parent PApplet.
   * <LI>The basic way to create bitmap images is to use the <A
   * HREF="http://processing.org/reference/saveFrame_.html">saveFrame()</A>
   * function.
   * <LI>If you want to create a really large scene and write that,
   * first make sure that you've allocated a lot of memory in the Preferences.
   * <LI>If you want to create images that are larger than the screen,
   * you should create your own PGraphics object, draw to that, and use
   * <A HREF="http://processing.org/reference/save_.html">save()</A>.
   * <PRE>
   *
   * PGraphics big;
   *
   * void setup() {
   *   big = createGraphics(3000, 3000);
   *
   *   big.beginDraw();
   *   big.background(128);
   *   big.line(20, 1800, 1800, 900);
   *   // etc..
   *   big.endDraw();
   *
   *   // make sure the file is written to the sketch folder
   *   big.save("big.tif");
   * }
   *
   * </PRE>
   * <LI>It's important to always wrap drawing to createGraphics() with
   * beginDraw() and endDraw() (beginFrame() and endFrame() prior to
   * revision 0115). The reason is that the renderer needs to know when
   * drawing has stopped, so that it can update itself internally.
   * This also handles calling the defaults() method, for people familiar
   * with that.
   * <LI>With Processing 0115 and later, it's possible to write images in
   * formats other than the default .tga and .tiff. The exact formats and
   * background information can be found in the developer's reference for
   * <A HREF="http://dev.processing.org/reference/core/javadoc/processing/core/PImage.html#save(java.lang.String)">PImage.save()</A>.
   * </UL>
   *
   * @webref rendering
   * @param w width in pixels
   * @param h height in pixels
   * @param renderer Either P2D, P3D, or PDF
   * @see PGraphics#PGraphics
   *
   */
  public PGraphics createGraphics(int w, int h, String renderer) {
    return createGraphics(w, h, renderer, null);
  }


  /**
   * Create an offscreen graphics surface for drawing, in this case
   * for a renderer that writes to a file (such as PDF or DXF).
   * @param path the name of the file (can be an absolute or relative path)
   */
  public PGraphics createGraphics(int w, int h,
                                  String renderer, String path) {
    return makeGraphics(w, h, renderer, path, false);
    /*
    if (path != null) {
      path = savePath(path);
    }
    PGraphics pg = makeGraphics(w, h, renderer, path, false);
    //pg.parent = this;  // why wasn't setParent() used before 3.0a6?
    //pg.setParent(this);  // make save() work
    // Nevermind, parent is set in makeGraphics()
    return pg;
    */
  }


//  public PGraphics makePrimaryGraphics(int wide, int high) {
//    return makeGraphics(wide, high, sketchRenderer(), null, true);
//  }


  /**
   * Version of createGraphics() used internally.
   * @param path A path (or null if none), can be absolute or relative ({@link PApplet#savePath} will be called)
   */
  protected PGraphics makeGraphics(int w, int h,
                                   String renderer, String path,
                                   boolean primary) {
//    String openglError = external ?
//      // This first one should no longer be possible
//      "Before using OpenGL, first select " +
//      "Import Library > OpenGL from the Sketch menu." :
//       // Welcome to Java programming! The training wheels are off.
//      "The Java classpath and native library path is not " +
//      "properly set for using the OpenGL library.";

    if (!primary && !g.isGL()) {
      if (renderer.equals(P2D)) {
        throw new RuntimeException("createGraphics() with P2D requires size() to use P2D or P3D");
      } else if (renderer.equals(P3D)) {
        throw new RuntimeException("createGraphics() with P3D or OPENGL requires size() to use P2D or P3D");
      }
    }

    try {
      Class<?> rendererClass =
        Thread.currentThread().getContextClassLoader().loadClass(renderer);

      Constructor<?> constructor = rendererClass.getConstructor(new Class[] { });
      PGraphics pg = (PGraphics) constructor.newInstance();

      pg.setParent(this);
      pg.setPrimary(primary);
      if (path != null) {
        pg.setPath(savePath(path));
      }
//      pg.setQuality(sketchQuality());
//      if (!primary) {
//        surface.initImage(pg, w, h);
//      }
      pg.setSize(w, h);

      // everything worked, return it
      return pg;

    } catch (InvocationTargetException ite) {
      String msg = ite.getTargetException().getMessage();
      if ((msg != null) &&
          (msg.indexOf("no jogl in java.library.path") != -1)) {
        // Is this true anymore, since the JARs contain the native libs?
        throw new RuntimeException("The jogl library folder needs to be " +
          "specified with -Djava.library.path=/path/to/jogl");

      } else {
        printStackTrace(ite.getTargetException());
        Throwable target = ite.getTargetException();
        /*
        // removing for 3.2, we'll see
        if (platform == MACOSX) {
          target.printStackTrace(System.out);  // OS X bug (still true?)
        }
        */
        throw new RuntimeException(target.getMessage());
      }

    } catch (ClassNotFoundException cnfe) {
//      if (cnfe.getMessage().indexOf("processing.opengl.PGraphicsOpenGL") != -1) {
//        throw new RuntimeException(openglError +
//                                   " (The library .jar file is missing.)");
//      } else {
      if (external) {
        throw new RuntimeException("You need to use \"Import Library\" " +
                                   "to add " + renderer + " to your sketch.");
      } else {
        throw new RuntimeException("The " + renderer +
                                   " renderer is not in the class path.");
      }

    } catch (Exception e) {
      if ((e instanceof IllegalArgumentException) ||
          (e instanceof NoSuchMethodException) ||
          (e instanceof IllegalAccessException)) {
        if (e.getMessage().contains("cannot be <= 0")) {
          // IllegalArgumentException will be thrown if w/h is <= 0
          // http://code.google.com/p/processing/issues/detail?id=983
          throw new RuntimeException(e);

        } else {
          printStackTrace(e);
          String msg = renderer + " needs to be updated " +
            "for the current release of Processing.";
          throw new RuntimeException(msg);
        }
      } else {
        /*
        if (platform == MACOSX) {
          e.printStackTrace(System.out);  // OS X bug (still true?)
        }
        */
        printStackTrace(e);
        throw new RuntimeException(e.getMessage());
      }
    }
  }


  /** Create default renderer, likely to be resized, but needed for surface init. */
  protected PGraphics createPrimaryGraphics() {
    return makeGraphics(sketchWidth(), sketchHeight(),
                        sketchRenderer(), sketchOutputPath(), true);
  }


  /**
   * ( begin auto-generated from createImage.xml )
   *
   * Creates a new PImage (the datatype for storing images). This provides a
   * fresh buffer of pixels to play with. Set the size of the buffer with the
   * <b>width</b> and <b>height</b> parameters. The <b>format</b> parameter
   * defines how the pixels are stored. See the PImage reference for more information.
   * <br/> <br/>
   * Be sure to include all three parameters, specifying only the width and
   * height (but no format) will produce a strange error.
   * <br/> <br/>
   * Advanced users please note that createImage() should be used instead of
   * the syntax <tt>new PImage()</tt>.
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   * Preferred method of creating new PImage objects, ensures that a
   * reference to the parent PApplet is included, which makes save() work
   * without needing an absolute path.
   *
   * @webref image
   * @param w width in pixels
   * @param h height in pixels
   * @param format Either RGB, ARGB, ALPHA (grayscale alpha channel)
   * @see PImage
   * @see PGraphics
   */
  public PImage createImage(int w, int h, int format) {
    PImage image = new PImage(w, h, format);
    image.parent = this;  // make save() work
    return image;
  }


  //////////////////////////////////////////////////////////////


  protected boolean insideDraw;

  /** Last time in nanoseconds that frameRate was checked */
  protected long frameRateLastNanos = 0;


  public void handleDraw() {
    //debug("handleDraw() " + g + " " + looping + " " + redraw + " valid:" + this.isValid() + " visible:" + this.isVisible());

    // canDraw = g != null && (looping || redraw);
    if (g == null) return;
    if (!looping && !redraw) return;
//    System.out.println("looping/redraw = " + looping + " " + redraw);

    // no longer in use by any of our renderers
//    if (!g.canDraw()) {
//      debug("g.canDraw() is false");
//      // Don't draw if the renderer is not yet ready.
//      // (e.g. OpenGL has to wait for a peer to be on screen)
//      return;
//    }

    // Store the quality setting in case it's changed during draw and the
    // drawing context needs to be re-built before the next frame.
//    int pquality = g.smooth;

    if (insideDraw) {
      System.err.println("handleDraw() called before finishing");
      System.exit(1);
    }

    insideDraw = true;
    g.beginDraw();
    if (recorder != null) {
      recorder.beginDraw();
    }

    long now = System.nanoTime();

    if (frameCount == 0) {
        // 3.0a5 should be no longer needed; handled by PSurface
        //surface.checkDisplaySize();

//        try {
        //println("Calling setup()");
      setup();
        //println("Done with setup()");

//        } catch (RendererChangeException e) {
//          // Give up, instead set the new renderer and re-attempt setup()
//          return;
//        }
//      defaultSize = false;

    } else {  // frameCount > 0, meaning an actual draw()
      // update the current frameRate
      double rate = 1000000.0 / ((now - frameRateLastNanos) / 1000000.0);
      float instantaneousRate = (float) (rate / 1000.0);
      frameRate = (frameRate * 0.9f) + (instantaneousRate * 0.1f);

      if (frameCount != 0) {
        handleMethods("pre");
      }

      // use dmouseX/Y as previous mouse pos, since this is the
      // last position the mouse was in during the previous draw.
      pmouseX = dmouseX;
      pmouseY = dmouseY;

        //println("Calling draw()");
      draw();
        //println("Done calling draw()");

      // dmouseX/Y is updated only once per frame (unlike emouseX/Y)
      dmouseX = mouseX;
      dmouseY = mouseY;

      // these are called *after* loop so that valid
      // drawing commands can be run inside them. it can't
      // be before, since a call to background() would wipe
      // out anything that had been drawn so far.
      dequeueEvents();

      handleMethods("draw");

      redraw = false;  // unset 'redraw' flag in case it was set
      // (only do this once draw() has run, not just setup())
    }
    g.endDraw();

//    if (pquality != g.smooth) {
//      surface.setSmooth(g.smooth);
//    }

    if (recorder != null) {
      recorder.endDraw();
    }
    insideDraw = false;

    if (frameCount != 0) {
      handleMethods("post");
    }

    frameRateLastNanos = now;
    frameCount++;
  }


//  /** Not official API, not guaranteed to work in the future. */
//  public boolean canDraw() {
//    return g != null && (looping || redraw);
//  }


  //////////////////////////////////////////////////////////////


/**
   * ( begin auto-generated from redraw.xml )
   *
   * Executes the code within <b>draw()</b> one time. This functions allows
   * the program to update the display window only when necessary, for
   * example when an event registered by <b>mousePressed()</b> or
   * <b>keyPressed()</b> occurs.
   * <br/><br/> structuring a program, it only makes sense to call redraw()
   * within events such as <b>mousePressed()</b>. This is because
   * <b>redraw()</b> does not run <b>draw()</b> immediately (it only sets a
   * flag that indicates an update is needed).
   * <br/><br/> <b>redraw()</b> within <b>draw()</b> has no effect because
   * <b>draw()</b> is continuously called anyway.
   *
   * ( end auto-generated )
 * @webref structure
 * @usage web_application
 * @see PApplet#draw()
 * @see PApplet#loop()
 * @see PApplet#noLoop()
 * @see PApplet#frameRate(float)
 */
  synchronized public void redraw() {
    if (!looping) {
      redraw = true;
//      if (thread != null) {
//        // wake from sleep (necessary otherwise it'll be
//        // up to 10 seconds before update)
//        if (CRUSTY_THREADS) {
//          thread.interrupt();
//        } else {
//          synchronized (blocker) {
//            blocker.notifyAll();
//          }
//        }
//      }
    }
  }

/**
   * ( begin auto-generated from loop.xml )
   *
   * Causes Processing to continuously execute the code within <b>draw()</b>.
   * If <b>noLoop()</b> is called, the code in <b>draw()</b> stops executing.
   *
   * ( end auto-generated )
 * @webref structure
 * @usage web_application
 * @see PApplet#noLoop()
 * @see PApplet#redraw()
 * @see PApplet#draw()
 */
  synchronized public void loop() {
    if (!looping) {
      looping = true;
    }
  }

/**
   * ( begin auto-generated from noLoop.xml )
   *
   * Stops Processing from continuously executing the code within
   * <b>draw()</b>. If <b>loop()</b> is called, the code in <b>draw()</b>
   * begin to run continuously again. If using <b>noLoop()</b> in
   * <b>setup()</b>, it should be the last line inside the block.
   * <br/> <br/>
   * When <b>noLoop()</b> is used, it's not possible to manipulate or access
   * the screen inside event handling functions such as <b>mousePressed()</b>
   * or <b>keyPressed()</b>. Instead, use those functions to call
   * <b>redraw()</b> or <b>loop()</b>, which will run <b>draw()</b>, which
   * can update the screen properly. This means that when noLoop() has been
   * called, no drawing can happen, and functions like saveFrame() or
   * loadPixels() may not be used.
   * <br/> <br/>
   * Note that if the sketch is resized, <b>redraw()</b> will be called to
   * update the sketch, even after <b>noLoop()</b> has been specified.
   * Otherwise, the sketch would enter an odd state until <b>loop()</b> was called.
   *
   * ( end auto-generated )
 * @webref structure
 * @usage web_application
 * @see PApplet#loop()
 * @see PApplet#redraw()
 * @see PApplet#draw()
 */
  synchronized public void noLoop() {
    if (looping) {
      looping = false;
    }
  }


  public boolean isLooping() {
    return looping;
  }


  //////////////////////////////////////////////////////////////


  BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
  private final Object eventQueueDequeueLock = new Object[0];


  /**
   * Add an event to the internal event queue, or process it immediately if
   * the sketch is not currently looping.
   */
  public void postEvent(processing.event.Event pe) {
    eventQueue.add(pe);

    if (!looping) {
      dequeueEvents();
    }
  }


  protected void dequeueEvents() {
    synchronized (eventQueueDequeueLock) {
      while (!eventQueue.isEmpty()) {
        Event e = eventQueue.remove();
        switch (e.getFlavor()) {
        case Event.MOUSE:
          handleMouseEvent((MouseEvent) e);
          break;
        case Event.KEY:
          handleKeyEvent((KeyEvent) e);
          break;
        }
      }
    }
  }


  //////////////////////////////////////////////////////////////


  /**
   * Actually take action based on a mouse event.
   * Internally updates mouseX, mouseY, mousePressed, and mouseEvent.
   * Then it calls the event type with no params,
   * i.e. mousePressed() or mouseReleased() that the user may have
   * overloaded to do something more useful.
   */
  protected void handleMouseEvent(MouseEvent event) {
    // http://dev.processing.org/bugs/show_bug.cgi?id=170
    // also prevents mouseExited() on the mac from hosing the mouse
    // position, because x/y are bizarre values on the exit event.
    // see also the id check below.. both of these go together.
    // Not necessary to set mouseX/Y on RELEASE events because the
    // actual position will have been set by a PRESS or DRAG event.
    // However, PRESS events might come without a preceeding move,
    // if the sketch window gains focus on that PRESS.
    final int action = event.getAction();
    if (action == MouseEvent.DRAG ||
        action == MouseEvent.MOVE ||
        action == MouseEvent.PRESS) {
      pmouseX = emouseX;
      pmouseY = emouseY;
      mouseX = event.getX();
      mouseY = event.getY();
    }

    // Get the (already processed) button code
    mouseButton = event.getButton();

    /*
    // Compatibility for older code (these have AWT object params, not P5)
    if (mouseEventMethods != null) {
      // Probably also good to check this, in case anyone tries to call
      // postEvent() with an artificial event they've created.
      if (event.getNative() != null) {
        mouseEventMethods.handle(new Object[] { event.getNative() });
      }
    }
    */

    // this used to only be called on mouseMoved and mouseDragged
    // change it back if people run into trouble
    if (firstMouse) {
      pmouseX = mouseX;
      pmouseY = mouseY;
      dmouseX = mouseX;
      dmouseY = mouseY;
      firstMouse = false;
    }

    mouseEvent = event;

    // Do this up here in case a registered method relies on the
    // boolean for mousePressed.

    switch (action) {
    case MouseEvent.PRESS:
      mousePressed = true;
      break;
    case MouseEvent.RELEASE:
      mousePressed = false;
      break;
    }

    handleMethods("mouseEvent", new Object[] { event });

    switch (action) {
    case MouseEvent.PRESS:
//      mousePressed = true;
      mousePressed(event);
      break;
    case MouseEvent.RELEASE:
//      mousePressed = false;
      mouseReleased(event);
      break;
    case MouseEvent.CLICK:
      mouseClicked(event);
      break;
    case MouseEvent.DRAG:
      mouseDragged(event);
      break;
    case MouseEvent.MOVE:
      mouseMoved(event);
      break;
    case MouseEvent.ENTER:
      mouseEntered(event);
      break;
    case MouseEvent.EXIT:
      mouseExited(event);
      break;
    case MouseEvent.WHEEL:
      mouseWheel(event);
      break;
    }

    if ((action == MouseEvent.DRAG) ||
        (action == MouseEvent.MOVE)) {
      emouseX = mouseX;
      emouseY = mouseY;
    }
  }


  /**
   * ( begin auto-generated from mousePressed.xml )
   *
   * The <b>mousePressed()</b> function is called once after every time a
   * mouse button is pressed. The <b>mouseButton</b> variable (see the
   * related reference entry) can be used to determine which button has been pressed.
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   *
   * If you must, use
   * int button = mouseEvent.getButton();
   * to figure out which button was clicked. It will be one of:
   * MouseEvent.BUTTON1, MouseEvent.BUTTON2, MouseEvent.BUTTON3
   * Note, however, that this is completely inconsistent across
   * platforms.
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mousePressed() { }


  public void mousePressed(MouseEvent event) {
    mousePressed();
  }


  /**
   * ( begin auto-generated from mouseReleased.xml )
   *
   * The <b>mouseReleased()</b> function is called every time a mouse button
   * is released.
   *
   * ( end auto-generated )
   * @webref input:mouse
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   * @see PApplet#mouseWheel(MouseEvent)
   */
  public void mouseReleased() { }


  public void mouseReleased(MouseEvent event) {
    mouseReleased();
  }


  public void mouseClicked(MouseEvent event) {

  }

  public void mouseDragged(MouseEvent event) {

  }

  public void mouseMoved(MouseEvent event) {

  }

  public void mouseEntered(MouseEvent event) {
  }


  public void mouseExited(MouseEvent e) {
      System.out.println(e);
  }

  /**
   * The event.getAmount() method returns negative values if the mouse wheel
   * if rotated up or away from the user and positive in the other direction.
   * On OS X with "natural" scrolling enabled, the values are opposite.
   *
   * @webref input:mouse
   * @param event the MouseEvent
   * @see PApplet#mouseX
   * @see PApplet#mouseY
   * @see PApplet#pmouseX
   * @see PApplet#pmouseY
   * @see PApplet#mousePressed
   * @see PApplet#mousePressed()
   * @see PApplet#mouseReleased()
   * @see PApplet#mouseClicked()
   * @see PApplet#mouseMoved()
   * @see PApplet#mouseDragged()
   * @see PApplet#mouseButton
   */
  public void mouseWheel(MouseEvent e) {
      System.out.println(e);
  }



  //////////////////////////////////////////////////////////////


  protected void handleKeyEvent(KeyEvent event) {

    // Get rid of auto-repeating keys if desired and supported
    if (!keyRepeatEnabled && event.isAutoRepeat()) return;

    keyEvent = event;
    key = event.getKey();
    keyCode = event.getKeyCode();

    switch (event.getAction()) {
    case KeyEvent.PRESS:
      Long hash = ((long) keyCode << Character.SIZE) | key;
      if (!pressedKeys.contains(hash)) pressedKeys.add(hash);
      keyPressed = true;
      keyPressed(keyEvent);
      break;
    case KeyEvent.RELEASE:
      pressedKeys.remove(((long) keyCode << Character.SIZE) | key);
      keyPressed = !pressedKeys.isEmpty();
      keyReleased(keyEvent);
      break;
    case KeyEvent.TYPE:
      keyTyped(keyEvent);
      break;
    }

    handleMethods("keyEvent", new Object[] { event });

    // if someone else wants to intercept the key, they should
    // set key to zero (or something besides the ESC).
    if (event.getAction() == KeyEvent.PRESS) {
      //if (key == java.awt.event.KeyEvent.VK_ESCAPE) {
      if (key == ESC) {
        exit();
      }
      // When running tethered to the Processing application, respond to
      // Ctrl-W (or Cmd-W) events by closing the sketch. Not enabled when
      // running independently, because this sketch may be one component
      // embedded inside an application that has its own close behavior.
      if (external &&
          event.getKeyCode() == 'W' &&
          ((event.isMetaDown() && platform == MACOSX) ||
           (event.isControlDown() && platform != MACOSX))) {
        // Can't use this native stuff b/c the native event might be NEWT
//      if (external && event.getNative() instanceof java.awt.event.KeyEvent &&
//          ((java.awt.event.KeyEvent) event.getNative()).getModifiers() ==
//            Toolkit.getDefaultToolkit().getMenuShortcutKeyMask() &&
//          event.getKeyCode() == 'W') {
        exit();
      }
    }
  }
  
  public void keyPressed(KeyEvent e) {
      System.out.println(e);   
  }

  public void keyReleased(KeyEvent e) {
      System.out.println(e);
  }

  public void keyTyped(KeyEvent e) {
      System.out.println(e);

  }



  //////////////////////////////////////////////////////////////


  public void focusGained() { }


  public void focusLost() {
    // TODO: if user overrides this without calling super it's not gonna work
    pressedKeys.clear();
  }


  //////////////////////////////////////////////////////////////

  // controlling time (playing god)


  /**
   * ( begin auto-generated from delay.xml )
   *
   * The delay() function causes the program to halt for a specified time.
   * Delay times are specified in thousandths of a second. For example,
   * running delay(3000) will stop the program for three seconds and
   * delay(500) will stop the program for a half-second.
   *
   * The screen only updates when the end of draw() is reached, so delay()
   * cannot be used to slow down drawing. For instance, you cannot use delay()
   * to control the timing of an animation.
   *
   * The delay() function should only be used for pausing scripts (i.e.
   * a script that needs to pause a few seconds before attempting a download,
   * or a sketch that needs to wait a few milliseconds before reading from
   * the serial port).
   *
   * ( end auto-generated )
   * @webref environment
   * @param napTime milliseconds to pause before running draw() again
   * @see PApplet#frameRate
   * @see PApplet#draw()
   */
  public void delay(int napTime) {
    try {
      Thread.sleep(napTime);
    } catch (InterruptedException e) { }
  }


  /**
   * ( begin auto-generated from frameRate.xml )
   *
   * Specifies the number of frames to be displayed every second. If the
   * processor is not fast enough to maintain the specified rate, it will not
   * be achieved. For example, the function call <b>frameRate(30)</b> will
   * attempt to refresh 30 times a second. It is recommended to set the frame
   * rate within <b>setup()</b>. The default rate is 60 frames per second.
   *
   * ( end auto-generated )
   * @webref environment
   * @param fps number of desired frames per second
   * @see PApplet#frameRate
   * @see PApplet#frameCount
   * @see PApplet#setup()
   * @see PApplet#draw()
   * @see PApplet#loop()
   * @see PApplet#noLoop()
   * @see PApplet#redraw()
   */
  public void frameRate(float fps) {
    surface.setFrameRate(fps);
  }


  /**
   * ( begin auto-generated from exit.xml )
   *
   * Quits/stops/exits the program. Programs without a <b>draw()</b> function
   * exit automatically after the last line has run, but programs with
   * <b>draw()</b> run continuously until the program is manually stopped or
   * <b>exit()</b> is run.<br />
   * <br />
   * Rather than terminating immediately, <b>exit()</b> will cause the sketch
   * to exit after <b>draw()</b> has completed (or after <b>setup()</b>
   * completes if called during the <b>setup()</b> function).<br />
   * <br />
   * For Java programmers, this is <em>not</em> the same as System.exit().
   * Further, System.exit() should not be used because closing out an
   * application while <b>draw()</b> is running may cause a crash
   * (particularly with P3D).
   *
   * ( end auto-generated )
   * @webref structure
   */
  public void exit() {
    if (surface.isStopped()) {
      // exit immediately, dispose() has already been called,
      // meaning that the main thread has long since exited
      exitActual();

    } else if (looping) {
      // dispose() will be called as the thread exits
      finished = true;
      // tell the code to call exitActual() to do a System.exit()
      // once the next draw() has completed
      exitCalled = true;

    } else if (!looping) {
      // if not looping, shut down things explicitly,
      // because the main thread will be sleeping
      dispose();

      // now get out
      exitActual();
    }
  }


  public boolean exitCalled() {
    return exitCalled;
  }


  /**
   * Some subclasses (I'm looking at you, processing.py) might wish to do something
   * other than actually terminate the JVM. This gives them a chance to do whatever
   * they have in mind when cleaning up.
   */
  public void exitActual() {
    try {
      System.exit(0);
    } catch (SecurityException e) {
      // don't care about applet security exceptions
    }
  }


  /**
   * Called to dispose of resources and shut down the sketch.
   * Destroys the thread, dispose the renderer,and notify listeners.
   * <p>
   * Not to be called or overriden by users. If called multiple times,
   * will only notify listeners once. Register a dispose listener instead.
   */
  public void dispose() {
    // moved here from stop()
    finished = true;  // let the sketch know it is shut down time

    // don't run the disposers twice
    if (surface.stopThread()) {

      // shut down renderer
      if (g != null) {
        g.dispose();
      }
      // run dispose() methods registered by libraries
      handleMethods("dispose");
    }

    if (platform == MACOSX) {
      try {
        final String td = "processing.core.ThinkDifferent";
        final Class<?> thinkDifferent = getClass().getClassLoader().loadClass(td);
        thinkDifferent.getMethod("cleanup").invoke(null);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

  }



  //////////////////////////////////////////////////////////////

  // CURSOR

  /**
   * Set the cursor type
   * @param kind either ARROW, CROSS, HAND, MOVE, TEXT, or WAIT
   */
  public void cursor(int kind) {
    surface.setCursor(kind);
  }


  /**
   * Replace the cursor with the specified PImage. The x- and y-
   * coordinate of the center will be the center of the image.
   */
  public void cursor(PImage img) {
    cursor(img, img.width/2, img.height/2);
  }


  /**
   * ( begin auto-generated from cursor.xml )
   *
   * Sets the cursor to a predefined symbol, an image, or makes it visible if
   * already hidden. If you are trying to set an image as the cursor, it is
   * recommended to make the size 16x16 or 32x32 pixels. It is not possible
   * to load an image as the cursor if you are exporting your program for the
   * Web and not all MODES work with all Web browsers. The values for
   * parameters <b>x</b> and <b>y</b> must be less than the dimensions of the image.
   * <br /> <br />
   * Setting or hiding the cursor generally does not work with "Present" mode
   * (when running full-screen).
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   * Set a custom cursor to an image with a specific hotspot.
   * Only works with JDK 1.2 and later.
   * Currently seems to be broken on Java 1.4 for Mac OS X
   * <p>
   * Based on code contributed by Amit Pitaru, plus additional
   * code to handle Java versions via reflection by Jonathan Feinberg.
   * Reflection removed for release 0128 and later.
   * @webref environment
   * @see PApplet#noCursor()
   * @param img any variable of type PImage
   * @param x the horizontal active spot of the cursor
   * @param y the vertical active spot of the cursor
   */
  public void cursor(PImage img, int x, int y) {
    surface.setCursor(img, x, y);
  }


  /**
   * Show the cursor after noCursor() was called.
   * Notice that the program remembers the last set cursor type
   */
  public void cursor() {
    surface.showCursor();
  }

  /**
   * ( begin auto-generated from noCursor.xml )
   *
   * Hides the cursor from view. Will not work when running the program in a
   * web browser or when running in full screen (Present) mode.
   *
   * ( end auto-generated )
   * <h3>Advanced</h3>
   * Hide the cursor by creating a transparent image
   * and using it as a custom cursor.
   * @webref environment
   * @see PApplet#cursor()
   * @usage Application
   */
  public void noCursor() {
    surface.hideCursor();
  }

  public void frameMoved(int x, int y) {
    if (!fullScreen) {
      System.err.println(EXTERNAL_MOVE + " " + x + " " + y);
    }
  }

  public void frameResized(int w, int h) {
  }

  public GraphicsWin() {
      this(0,0, DEFAULT_WIDTH, DEFAULT_HEIGHT, "", 0x808080);
  }
  public GraphicsWin(int x, int y, int w, int h, String title, int bgColor,
          int displayNum) {
    System.setProperty("sun.awt.noerasebackground", "true");
    // Remove 60fps limit on the JavaFX "pulse" timer
    System.setProperty("javafx.animation.fullspeed", "true");
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        uncaughtThrowable = e;
      }
    });
    try {
      // Call validate() while resize events are in progress
      Toolkit.getDefaultToolkit().setDynamicLayout(true);
    } catch (HeadlessException e) {
      System.err.println("Cannot run sketch without a display. Read this for possible solutions:");
      System.err.println("https://github.com/processing/processing/wiki/Running-without-a-Display");
      System.exit(1);
    }

    // So that the system proxy setting are used by default
    System.setProperty("java.net.useSystemProxies", "true");

    int[] location = null;
    int[] editorLocation = null;

    String name = null;

    int density = -1;

    if (platform == MACOSX) {
      try {
        final String td = "processing.core.ThinkDifferent";
        Class<?> thinkDifferent =
          Thread.currentThread().getContextClassLoader().loadClass(td);
        Method method =
          thinkDifferent.getMethod("init", new Class[] { PApplet.class });
        method.invoke(null, new Object[] { sketch });
      } catch (Exception e) {
        e.printStackTrace();  // That's unfortunate
      }
    }

    // Set the suggested display that's coming from the command line
    // (and most likely, from the PDE's preference setting).
    display = displayNum;

    // Set the suggested density that is coming from command line
    suggestedDensity = -1;
    present = false;
    sketch.handleSettings();
}
