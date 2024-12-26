//@ts-ignore
const n_rows: number = n_rows;

// get html references
const d = document;
const input = <HTMLInputElement>d.getElementById("modify-cell");
const label = <HTMLLabelElement>d.getElementById("modify-cell-label");
const submit_button = <HTMLButtonElement>d.getElementById("modify-cell-submit");
if (!(input && label && submit_button))
  throw new Error(
    "Html file not correct format or you didn't import with 'defer'"
  );

// * types
/** request body to use to the server when informing of changes */
type Update = {
  cell: string;
  formula: string;
};

/** json of the changes to do from the server */
type ServerChanges = {
  changes: [
    {
      cell: string;
      value: string;
      formula: string;
    }
  ];
};

/** json of error from the server */
type ServerPostError = {
  reason: string;
};

/** type of the data structure used to save the data about the cells */
type CellValueMap = {
  [cell_id: string]: {
    formula: string;
    shown_value: string;
  };
};

// * global variables
/** current cell selected */
let current_cell_id: string = "";

const url = "http://localhost:8080/Spreadsheet_war_exploded/server";
// const url = '/server';

/** data structure to save the data about the cells */
const cell_value_map: CellValueMap = {};
initCellValueMap();

// * functions
/** initialize the cell_value_map by asking the server */
function initCellValueMap() {
  for (let i = 0; i < n_rows; i++)
    for (let j = 1; j <= n_rows; j++) {
      const letter = String.fromCharCode("A".charCodeAt(0) + i);
      cell_value_map[letter + j] = {
        formula: "",
        shown_value: "",
      };
    }

  getStartingValues();
}

/** get the starting values from the server */
async function getStartingValues() {
  try {
    const res_json = await fetch(url, {
      method: "GET",
    });
    const res: ServerChanges & ServerPostError = await res_json.json();

    if (res.changes) {
      modifyCells(<ServerChanges>res);
    } else {
      alert(`Server returned error: ${res.reason}`);
    }
  } catch (e: any) {
    console.error(e);
    // alert(`Something went wrong!\nError receiving changes: ${e.message}`);
  }
}

/**
 * function called when the cell is not clicked anymore,
 * either because a new one is clicked or the cell is double clicked.
 * If the formula changed, send the server a change cell request
 */
function resetClick() {
  const formula = input.value;
  input.value = "";
  input.disabled = true;
  label.textContent = "Select a cell to modify it";
  submit_button.disabled = true;

  const previous_cell = document.getElementById(current_cell_id);
  if (previous_cell) {
    previous_cell.style.outline = "0px solid black";
    previous_cell.textContent = cell_value_map[current_cell_id].shown_value;
    if (formula !== cell_value_map[current_cell_id].formula)
      serverParseFormula(formula);
  }
  current_cell_id = "";
}

/** enable the form input and button to modify the cell value */
function enableModify(cell_id: string) {
  input.disabled = false;
  input.name = `new_${cell_id}_value`;
  input.focus();
  label.textContent = `Selected cell: ${cell_id}`;
  submit_button.disabled = false;
}

/**
 * function called from a cell onclick event.
 * It changes the current_cell, by coloring it red and
 * enables input modification.
 */
function clickedCell(cell_id: string) {
  const previous_cell_id = current_cell_id;
  resetClick();

  if (cell_id === previous_cell_id) return;

  const current_cell = document.getElementById(cell_id);
  if (!current_cell) return;
  current_cell.style.outline = "5px solid green";
  const formula = cell_value_map[cell_id].formula;
  current_cell.textContent = formula;
  input.value = formula;

  current_cell_id = cell_id;
  enableModify(cell_id);
}

/** when something is typed in the input, changes the cell text to keep up */
function inputChanged() {
  const current_cell = document.getElementById(current_cell_id);
  if (current_cell) {
    current_cell.textContent = input.value;
  }
}

/**
 * when the input form is submitted, send data to the server.
 * It prevents default behaviour
 */
function submitInput(event: SubmitEvent) {
  event.preventDefault();

  const current_cell = document.getElementById(current_cell_id);
  if (current_cell) {
    resetClick();
  }
}

/** inform the server of a cell modification, then use modifyCells() */
async function serverParseFormula(formula: string) {
  if (formula.includes('"')) return alert('Invalid " character inside input');
  const server_req: Update = {
    cell: current_cell_id,
    formula,
  };

  try {
    const res_json = await fetch(url, {
      method: "POST",
      body: JSON.stringify(server_req),
    });
    const res: ServerChanges & ServerPostError = await res_json.json();

    if (res.changes) {
      modifyCells(<ServerChanges>res);
    } else {
      console.log(`POST request ERROR: ${res.reason}`);
      alert(`You made an invalid request\nReason: ${res.reason}`);
    }
  } catch (e: any) {
    console.error(e);
    alert(`Something went wrong!\nError sending changes: ${e.message}`);
  }
}

/** modify the cells as said from the server */
async function modifyCells(res: ServerChanges) {
  if (!res.changes.length) return;

  console.table(res.changes);
  for (const c of res.changes) {
    const { cell: cell_id, value, formula } = c;

    const cell = document.getElementById(cell_id);
    if (!cell) continue;

    /** if the formula is empty, we want to show the cell as empty */
    const new_value = formula ? value : "";
    cell.textContent = new_value;
    cell_value_map[cell_id].shown_value = new_value;
    cell_value_map[cell_id].formula = formula;
  }
}
