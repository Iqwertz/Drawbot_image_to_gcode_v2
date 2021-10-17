///////////////////////////////////////////////////////////////////////////////////////////////////////
// A class to describe one line segment
//
// Because of a bug in processing.org the MULTIPLY blendMode does not take into account the alpha of
// either source or destination.  If this gets corrected, tweaks to the stroke alpha might be more 
// representative of a Copic marker.  Right now it over emphasizes the darkening when overlaps
// of the same pen occur.

class botLine {
  constructor(pen_down_, pen_number_, x1_, y1_, x2_, y2_) {
    this.pen_down = pen_down_;
    this.pen_number = pen_number_;
    this.x1 = x1_;
    this.y1 = y1_;
    this.x2 = x2_;
    this.y2 = y2_;
  }

  render_with_copic() {
    if (this.pen_down) {
      let c = color(0, 0, 0);
      //stroke(c, 255-brightness(c));
      stroke(c);
      //strokeWeight(2);
      //blendMode(BLEND);
      this.line(x1, y1, x2, y2);
    }
  }

}

///////////////////////////////////////////////////////////////////////////////////////////////////////
