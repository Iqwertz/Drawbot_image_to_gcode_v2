///////////////////////////////////////////////////////////////////////////////////////////////////////
// A class to describe all the line segments
class botDrawing {
  
  constructor() {
    this.line_count = 0;
    this.lines = [];
  }

  render_last () {
    this.lines[this.line_count].render_with_copic();
  }
  
  render_all () {
    for (let i=1; i<this.line_count; i++) {
      this.lines[i].render_with_copic();
    }
  }
  
  render_some (line_count) {
    for (let i=1; i<line_count; i++) {
      this.lines[i].render_with_copic();
    }
  }

  addline(pen_number_,pen_down_,x1_,y1_,x2_,y2_) {
    this.line_count++;
    this.lines[this.line_count] = new botLine(pen_down_, pen_number_, x1_, y1_, x2_, y2_);
  }
  
  get_line_count() {
    return this.line_count;
  }

}
